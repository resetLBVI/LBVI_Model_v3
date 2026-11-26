#!/usr/bin/env python3
"""
LBVI Parameter Sweep / Batch Runner
===================================
Reads sweep_config.json, expands it into a list of runs, writes one parameter
CSV per run, and launches each as a headless lbvi.LBVIEnvironment JVM (in
parallel or sequentially). Each run's outputs go into runs/<runId>/ via the
model's -outputDir flag, so parallel runs never collide.

Parameters are passed to the model through a generated per-run CSV (the model's
own loadParameterList reads it by reflection), NOT through -key value flags, so
adding/renaming model parameters never requires editing this script - only the
config's "sweep"/"fixed" sections.

Usage:
    python3 run_sweep.py                        # uses sweep_config.json
    python3 run_sweep.py --config my_sweep.json
    python3 run_sweep.py --dry-run              # print commands, write nothing, run nothing
"""

# Make type hints like "str | None" lazy strings, so this script runs on Python 3.7+ (not just 3.10+).
from __future__ import annotations

import argparse
import csv
import itertools
import json
import sys
import subprocess
from concurrent.futures import ProcessPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path

ENTRY_CLASS = "lbvi.LBVIEnvironment"
# keys in a run dict that are NOT model parameters (handled as CLI flags, not written to the param CSV)
META_KEYS = {"seed", "runId"}


class Tee:
    """Mirror everything written to stdout into a log file as well."""
    def __init__(self, log_path: Path):
        self.terminal = sys.stdout
        self.log = open(log_path, "a", buffering=1)  # line-buffered

    def write(self, msg):
        self.terminal.write(msg)
        self.log.write(msg)

    def flush(self):
        self.terminal.flush()
        self.log.flush()


def expand_sweep_value(val):
    """A sweep value is either a list of values, or {"range":[start,stop(,step)]} (stop exclusive)."""
    if isinstance(val, dict) and "range" in val:
        return list(range(*val["range"]))
    if isinstance(val, list):
        return val
    return [val]  # a bare scalar is treated as a one-value list


def _strip_quotes(v: str) -> str:
    """Mirror the model's stripQuotes: drop one pair of surrounding double quotes and un-double inner ones."""
    if len(v) >= 2 and v.startswith('"') and v.endswith('"'):
        return v[1:-1].replace('""', '"')
    return v


def read_base_params(path: str) -> dict:
    """Read a 'parameter,value' baseline CSV (e.g. paramListDefault.csv) into a dict, the SAME way the model
    reads it: split each line on the FIRST comma only (values may contain commas inside quotes), strip a BOM
    on the first line, skip the header and blanks. These are the lowest-priority values; 'fixed' then the
    swept value override them per run."""
    p = Path(path)
    if not p.exists():
        sys.exit(f"base_params file not found: {p}")
    base = {}
    for i, line in enumerate(p.read_text().splitlines()):
        if i == 0:
            line = line.lstrip("﻿")   # strip UTF-8 BOM on the first line
        if not line.strip() or "," not in line:
            continue
        key, val = line.split(",", 1)
        key = key.strip()
        if key == "" or key == "parameter":   # header row or blank key
            continue
        base[key] = _strip_quotes(val.strip())
    return base


def build_run_list(config: dict, base: dict) -> list[dict]:
    """Return a list of dicts, each describing one run (model params + seed + runId). Precedence per run,
    lowest to highest: base_params -> fixed -> the swept value."""
    sweep = {k: expand_sweep_value(v) for k, v in config.get("sweep", {}).items()}
    fixed = config.get("fixed", {})
    seeds = config.get("seeds", [1])
    mode = config.get("mode", "one_at_a_time")
    seed_per_run = bool(config.get("seed_per_run", False))

    sweep_keys = list(sweep.keys())
    sweep_values = list(sweep.values())
    defaults = {k: v[0] for k, v in sweep.items()}  # first value = OAT baseline

    param_sets = []  # list of {param: value} dicts (no seed yet)
    if mode == "full_factorial":
        for combo in itertools.product(*sweep_values):
            param_sets.append(dict(zip(sweep_keys, combo)))
    elif mode == "one_at_a_time":
        param_sets.append(dict(defaults))                       # baseline
        for key, values in sweep.items():
            for v in values:
                if v == defaults[key]:
                    continue
                ps = dict(defaults)
                ps[key] = v
                param_sets.append(ps)
    else:
        raise ValueError(f"Unknown mode: {mode!r}. Use 'one_at_a_time' or 'full_factorial'.")

    runs = []
    idx = 0
    if seed_per_run:
        base_seed = int(seeds[0])
        for ps in param_sets:
            idx += 1
            seed = base_seed + (idx - 1)
            run = dict(base); run.update(fixed); run.update(ps)  # base -> fixed -> swept
            run["seed"] = seed
            run["runId"] = f"run_{idx:04d}_s{seed}"
            runs.append(run)
    else:
        for ps in param_sets:
            for seed in seeds:
                idx += 1
                run = dict(base); run.update(fixed); run.update(ps)  # base -> fixed -> swept
                run["seed"] = int(seed)
                run["runId"] = f"run_{idx:04d}_s{seed}"
                runs.append(run)
    return runs


def epsg_cache_dir(jar_path: Path) -> Path:
    """Absolute, shared GeoTools EPSG-HSQL cache directory used by every run (keeps it off the Desktop
    and lets parallel JVMs share one warm DB)."""
    return jar_path.resolve().parent / "geotools_epsg_cache"


def write_param_csv(run: dict, run_dir: Path) -> Path:
    """Write this run's model parameters (everything except the meta keys) to runs/<runId>/params.csv
    in the model's 'parameter,value' format, and return its absolute path."""
    run_dir.mkdir(parents=True, exist_ok=True)
    csv_path = run_dir / "params.csv"
    with open(csv_path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["parameter", "value"])
        for k, v in run.items():
            if k in META_KEYS:
                continue
            w.writerow([k, _fmt(v)])
    return csv_path.resolve()


def _fmt(v):
    if isinstance(v, bool):
        return "true" if v else "false"   # Java Boolean.parseBoolean wants lowercase
    return str(v)


def build_command(jar_path: Path, run: dict, run_dir: Path, param_csv: Path, heap: str) -> list[str]:
    """Build the java command for a single run."""
    return [
        "java",
        f"-Xmx{heap}",
        # Pin the GeoTools EPSG-HSQL cache to one fixed absolute dir so all parallel jobs share the
        # same warm DB (avoids cold-start lock contention / a corrupt DB between concurrent JVMs).
        f"-DEPSG-HSQL.directory={epsg_cache_dir(jar_path)}",
        "-cp", str(jar_path.resolve()),
        ENTRY_CLASS,
        "-paramFile", str(param_csv),
        "-seed", str(run["seed"]),
        "-outputDir", str(run_dir.resolve()),
    ]


def log_has_oom(log_file: Path) -> bool:
    """True if the run's log contains a Java OutOfMemoryError (scanned in chunks, with overlap)."""
    needle = b"OutOfMemoryError"
    overlap = len(needle) - 1
    try:
        with open(log_file, "rb") as f:
            tail = b""
            while True:
                chunk = f.read(1 << 20)
                if not chunk:
                    return False
                if needle in tail + chunk:
                    return True
                tail = chunk[-overlap:]
    except OSError:
        return False


def _emit(msg: str, log_path: str | None) -> None:
    """Print a line; also append it to the sweep log when running inside a parallel worker process
    (whose stdout is not the Tee installed in main)."""
    print(msg)
    if log_path and not isinstance(sys.stdout, Tee):
        try:
            with open(log_path, "a", buffering=1) as f:
                f.write(msg + "\n")
        except OSError:
            pass


def execute_run(jar_str: str, run: dict, dry_run: bool, heap: str, log_path: str | None) -> tuple[str, int, bool]:
    """Run one simulation. Returns (runId, return_code, hit_oom)."""
    jar_path = Path(jar_str)
    run_id = run["runId"]
    run_dir = Path("runs") / run_id
    param_csv = run_dir / "params.csv"
    cmd = build_command(jar_path, run, run_dir, param_csv.resolve(), heap)

    if dry_run:
        print("DRY RUN:", " ".join(cmd))
        return run_id, 0, False

    write_param_csv(run, run_dir)          # creates run_dir + params.csv
    log_file = run_dir / "stdout.log"
    _emit(f"[{datetime.now():%H:%M:%S}] START  {run_id}", log_path)
    with open(log_file, "w") as f:
        proc = subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT)

    oom = proc.returncode != 0 and log_has_oom(log_file)
    status = "OK" if proc.returncode == 0 else (
        "FAILED (OutOfMemory)" if oom else f"FAILED (rc={proc.returncode})")
    _emit(f"[{datetime.now():%H:%M:%S}] {status:22s}  {run_id}  -> {log_file}", log_path)
    return run_id, proc.returncode, oom


def write_manifest(runs: list[dict], config: dict) -> None:
    """Write runs/sweep_manifest.csv (one row per run, every param) and runs/sweep_manifest.json."""
    manifest_dir = Path("runs")
    manifest_dir.mkdir(parents=True, exist_ok=True)
    all_keys = sorted({k for r in runs for k in r})

    with open(manifest_dir / "sweep_manifest.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=all_keys)
        w.writeheader()
        w.writerows(runs)
    with open(manifest_dir / "sweep_manifest.json", "w") as f:
        json.dump({"generated": datetime.now().isoformat(), "config": config, "runs": runs}, f, indent=2)
    print(f"Manifest written: {manifest_dir/'sweep_manifest.csv'} (+ .json)\n")


def main():
    ap = argparse.ArgumentParser(description="LBVI parameter sweep / batch runner")
    ap.add_argument("--config", default="sweep_config.json", help="path to config JSON")
    ap.add_argument("--dry-run", action="store_true", help="print commands without writing or running")
    args = ap.parse_args()

    config_path = Path(args.config)
    if not config_path.exists():
        sys.exit(f"Config file not found: {config_path}")
    config = json.loads(config_path.read_text())

    jar_path = Path(config["jar"])
    if not args.dry_run and not jar_path.exists():
        sys.exit(f"JAR not found: {jar_path}\nBuild it first:  mvn -q clean package -DskipTests")

    n_jobs = int(config.get("parallel_jobs", 1))
    heap = str(config.get("heap", "4g"))

    # Optional baseline layer: every run starts from these values (then fixed, then the swept value override),
    # so you keep one source of truth (e.g. paramListDefault.csv) and only list what the sweep changes.
    base_path = config.get("base_params", "")
    base = read_base_params(base_path) if base_path else {}

    sweep_log = str((Path("runs") / "sweep_run.log").resolve())
    if not args.dry_run:
        Path("runs").mkdir(parents=True, exist_ok=True)
        sys.stdout = Tee(Path("runs") / "sweep_run.log")
        print(f"\n===== Sweep started {datetime.now().isoformat()} =====")

    runs = build_run_list(config, base)
    print(f"\nmode={config.get('mode','one_at_a_time')}  seed_per_run={config.get('seed_per_run', False)}  "
          f"base_params={base_path or '(none)'} ({len(base)} params)  "
          f"total_runs={len(runs)}  parallel_jobs={n_jobs}  heap={heap}\n")

    if args.dry_run:
        print("--- DRY RUN: commands that would be executed ---")
        for run in runs:
            run_dir = Path("runs") / run["runId"]
            cmd = build_command(jar_path, run, run_dir, (run_dir / "params.csv").resolve(), heap)
            print(" ".join(cmd))
        print(f"\n({len(runs)} runs; nothing written)")
        return

    write_manifest(runs, config)

    # ---- pre-warm the shared GeoTools EPSG cache ----
    # On a cold start GeoTools builds a ~32 MB HSQL EPSG DB in the shared cache dir. Letting all parallel
    # JVMs build it at once causes lock contention / a corrupt DB, so when cold we run the first job alone
    # to build it, then fan the rest out against the warm DB.
    cache_dir = epsg_cache_dir(jar_path)
    cache_dir.mkdir(parents=True, exist_ok=True)
    is_warm = lambda: any(cache_dir.rglob("EPSG_creation_marker.txt"))
    parallel = runs
    failed, oom_runs = [], []

    if n_jobs > 1 and len(runs) > 1 and not is_warm():
        warm = runs[0]
        print(f"EPSG cache cold ({cache_dir}). Pre-warming with {warm['runId']} before parallel fan-out...\n")
        _, rc, oom = execute_run(str(jar_path), warm, False, heap, sweep_log)
        if rc != 0: failed.append(warm["runId"])
        if oom: oom_runs.append(warm["runId"])
        if not is_warm():
            print("WARNING: pre-warm finished but EPSG marker not found; parallel runs may rebuild the cache.\n")
        parallel = runs[1:]

    # ---- execute ----
    if n_jobs == 1:
        for run in parallel:
            _, rc, oom = execute_run(str(jar_path), run, False, heap, sweep_log)
            if rc != 0: failed.append(run["runId"])
            if oom: oom_runs.append(run["runId"])
    else:
        with ProcessPoolExecutor(max_workers=n_jobs) as pool:
            futs = {pool.submit(execute_run, str(jar_path), run, False, heap, sweep_log): run["runId"]
                    for run in parallel}
            for fut in as_completed(futs):
                run_id, rc, oom = fut.result()
                if rc != 0: failed.append(run_id)
                if oom: oom_runs.append(run_id)

    # ---- summary ----
    print(f"\n{'='*60}")
    print(f"Sweep complete.  {len(runs) - len(failed)}/{len(runs)} runs succeeded.")
    if failed:
        print("FAILED runs:")
        for r in failed:
            print(f"  {r}{'  [OutOfMemory]' if r in oom_runs else ''}")
    if oom_runs:
        print(f"\n!! {len(oom_runs)} run(s) hit OutOfMemoryError with heap={heap}. Raise \"heap\" and/or lower")
        print("   \"parallel_jobs\" (keep heap x parallel_jobs under total RAM), then re-run.")
    print(f"\nResults are in:  runs/")


if __name__ == "__main__":
    main()
