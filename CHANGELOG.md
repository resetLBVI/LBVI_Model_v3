# Changelog

A running log of code changes and bug fixes to the RESET LBVI model, so the team can track what changed, when, and why — and make sure nothing gets overlooked.

**How to add an entry:** put the newest change at the top. Use the date (YYYY-MM-DD), tag it `Change`, `Bug fix`, `Feature`, or `Refactor`, and note the files touched and the reason. Keep it short but specific enough to reproduce or reverse.

---

## 2026-11-26 — Bug fix: globally-unique NestID (minted at first egg) + move NNests++ to first egg

**Bug (user-reported):** `logMortalityEvents.csv` showed more nest-abandonment events than total nests — impossible. Root cause: `nestID` was a **per-female counter** (`LBVIAgent` field, `nestID++` per build, reset to 0 each year) yet used as the global `nestMap` key **and** written into every log row (success + mortality). Across females and years the ids collided, so distinct-NestID counts under-counted real nests while per-nest event tallies exceeded them. (`nestMap` is never read by key, so the collision only corrupted the logged id — the user-facing symptom.)

**Design (decided by user):** a nest "becomes a nest" when the first egg is laid in a **completed** nest. At that moment it gets its NestID and the nest-attempt counter is incremented.

- **`LBVIEnvironment.java`:** new never-reset global id source next to `nestMap` — `private int nestIDCounter` + `public int nextNestID()` (returns `++counter`, so ids start at 1; 0 means "not yet a nest"). Unique across all females AND all years.
- **`LBVIAgent.java`:**
  - Removed the buggy per-female `int nestID` field and its annual reset. The current nest is now tracked via the repurposed `vireoNestID` (set at first egg).
  - `nestBuilding`: the `Nest` is created with id `0` and is **not** put in `nestMap` yet; `this.nestID++` removed; `vireoNestID` cleared to 0 at build start (so building-stage logs don't show a previous nest's id).
  - `state.nNests++` **moved** from building completion to the first-egg point in `eggLaying`. Minting is keyed on `nest.nestID == 0` (not the egg count) so re-laying after a same-day egg-predation drop does not mint a second id for the same nest.
  - All 7 log sites now write `vireoNestID` (hatch, fledge, fledgling-independence, clutch completion, both `logBuildingEvent` branches, `logDailyMortalityForNest`).
- **`Nest.java`:** no change (ctor still takes the id param, now passed `0`; its own log/`nestMap.remove` sites fire post-first-egg and read the real minted id).
- **Output changes:** every real nest now has a globally-unique NestID in all logs → distinct-NestID counts in `logMortalityEvents.csv` are now ≤ `NNests` (bug fixed). **`"nest completion"` and `"false start"` rows now carry NestID `0`** (they precede the first egg, so the nest has no id yet — accepted by design). `NNests` now excludes completed-but-eggless nests (none in practice, since the first egg lays on the next step). `NPairs`/`hasNestedThisYear` deliberately left at building completion.
- Compiles clean; 23/23 tests pass.

## 2026-11-25 — Fix: consistent EventType label for nestling-stage whole-nest predation

`LBVIAgent.java:340` logged the nestling-stage whole-nest predation event as EventType `"daily nest mortality"` — the only mortality label that broke the model's consistent `predator-*` vocabulary. It is the same kind of event as the three other whole-nest predations (incubation lines 281/305 and nestling line 364), which all log `"predator-wholeNest"`. Changed line 340 to `"predator-wholeNest"` so the nestling stage matches both its own other branch and the incubation stage. **Output change:** `logMortalityEvents.csv` no longer emits `"daily nest mortality"`; those rows now read `"predator-wholeNest"` (EntityType still `NESTLING`). No in-repo code/analysis referenced the old string; external analysis scripts that filtered on `"daily nest mortality"` must switch to `"predator-wholeNest"`. No behavioral change (same event, same nest kill, same RENEST transition) — label only.

## 2026-11-25 — Feature: headless batch/sweep runner (`run_sweep.py` + `sweep_config.json`) + `-outputDir`

Adds a PSHB-style launcher so a batch of headless runs (e.g. the 100 PSHB beetle replicates) can be run in parallel with JVM heap control and per-run output folders. Modeled on the PSHB project's runner.

- **`LBVIEnvironment.main` (headless entry):** now accepts named flags `-paramFile`, `-seed`, `-outputDir` (and `-runId NAME` = sugar for `-outputDir runs/NAME`), while the old positional form `[paramFile] [seed]` still works (back-compat; positional is used when no arg starts with `-`). New `redirectOutputToDir(dir)` prefixes **every** output file (the six logs + both TEMP diagnostic logs + `paramList.csv`) into `dir`, so parallel runs never collide (output paths are intentionally not loadable from the param CSV, so this flag is the mechanism). GUI entry (`LBVIWorldWithUI`) is unaffected.
- **`run_sweep.py`:** reads `sweep_config.json`, expands it into runs (modes `one_at_a_time` / `full_factorial`; a sweep value may be a list or `{"range":[start,stop(,step)]}`), writes one `parameter,value` CSV per run (into `runs/<runId>/params.csv`) and launches `java -Xmx<heap> -DEPSG-HSQL.directory=<shared cache> -cp <jar> lbvi.LBVIEnvironment -paramFile … -seed … -outputDir runs/<runId>`. Params go through the model's reflection CSV loader (not `-key value` flags), so adding/renaming model params needs only a config edit, never a script edit. Also: parallel execution (`parallel_jobs`), GeoTools EPSG cold-start pre-warm (one job first, then fan out against the warm DB), per-run `stdout.log`, `runs/sweep_manifest.csv`/`.json`, OOM detection, and a `--dry-run` that prints commands without writing or running. `seed_per_run:true` gives each run a distinct reproducible seed (`seeds[0] + runIndex`) instead of the seeds×params cartesian.
- **`sweep_config.json`:** default config = the 100-run beetle batch (`mpBeetleRunNum` `{"range":[1,101]}`, Pendleton, `seed_per_run:true`). Input-file paths are absolute in `fixed` (edit per machine) — or omit them and colocate the jar with `RESET_LBVI_inputData`.
- **`base_params` baseline layer (added same day):** `run_sweep.py` optionally reads a `parameter,value` baseline CSV (e.g. `paramListDefault.csv`) named by `base_params` in the config, parsed the same way the model parses it (first-comma split, BOM-/quote-tolerant). Per run the precedence is **base_params → fixed → swept value**, so the team keeps ONE source of truth for all ~75 params and lists only what the sweep changes. `""`/absent = no baseline (model in-code defaults). Documented in `HOW_TO_RUN_WITH_PARAMS.md`.
- **Distribution bundle (added same day):** `run_sweep.py` gains `from __future__ import annotations` so it runs on Python 3.7+ (was 3.10+ due to `str | None` hints). New `sweep_config_portable.json` (all paths relative → works unedited when everything sits in one folder) and `README_sweep.md` (one-folder layout, prerequisites, a mode/command/required-files table for GUI vs single-headless vs batch, and sweep setup/edit/results notes). The non-portable `sweep_config.json` (absolute paths, for local dev) is kept.
- **Deployment note:** the model resolves *relative* input paths against the jar's own directory (see `OutputWriter.getFileName`), so for a batch either use absolute input paths (as the default config does) or put the jar in the same folder as `RESET_LBVI_inputData`.

## 2026-11-25 — TEMP diagnostic: per-territory beetle-impact trace log (verification; to be removed)

Temporary verification log so the team can confirm the beetle process is correct end to end. **Remove after verification** (see memory `temp-beetle-impact-logging`), like the existing TEMP dispersal-outcome log.

- **`logBeetleImpact.csv`** — columns `currentStep, Year, Date, terrID, terrQuality, terrVegHealth, terrBeetleDamage, terrPreBeetleDamageQuality`. One row the moment a territory is damaged, then one row each season-end while it is in its recovery countdown, up to and including the recovery row — so each damaged territory's full trajectory (quality→0, health→very stressed, counter ticking 4→0, quality restored, health→healthy) is traceable.
- **Gating:** only written when `mpBeetleImpact == true` **and** `debugLog == true`. Otherwise the writer (`beetleImpactWriter`) is never created and the `LBVIEnvironment.logBeetleState(terr)` helper is a no-op. No performance/output impact when off.
- **`LBVIEnvironment.java`:** new TEMP fields `logBeetleImpactFile` / `beetleImpactWriter`, writer created in the TEMP block of `start()`, new `logBeetleState(VegInfoIdentifier)` helper. **`Beetles/ActBeetleImpact.java`:** calls `logBeetleState` after damaging a territory and at each season-end tick while damaged.
- **Tests:** added a 6th `ActBeetleImpactTest` case asserting the log file traces damage → recovery (header + damage row + 4 season-end rows; first row shows quality 0/health 1/counter 4, last shows quality restored/health 3/counter 0). 23/23 pass, compiles clean.

## 2026-11-24 — Feature: PSHB beetle impacts on riparian vegetation (ODD 3.1) + recovery (ODD 3.22)

Implements annual PSHB beetle damage to Territories and their multi-season recovery.

- **New `lbvi/Beetles/ActBeetleImpact.java`** (Steppable, scheduled repeating at ordering 0):
  - **Damage (ODD 3.1):** on day 0 of each breeding year (before spring arrival), every Territory listed for the current simulation year has `terrVegHealth` → "very stressed" (= `VegHealth` code 1, SEVERELYSTRESSED — see ODD_correction on the label), `terrQuality` copied to `terrPreBeetleDamageQuality`, `terrQuality` → 0 (a quality-0 territory is already excluded from settlement in `LBVIAgent.findPotentialTerritoryList`), and `terrBeetleDamage` → `mpBeetleDamageCounter`. A territory re-listed while still mid-countdown does NOT re-copy its (now 0) quality; it only refreshes the counter, so the saved pre-damage value survives.
  - **Recovery (ODD 3.22):** `recoverFromBeetleDamage(state)`, called from `LBVIObserver` at season end **after** `logReproductivePerformance.csv` is written. Every Territory with `terrBeetleDamage ≥ 1` decrements by one; any reaching 0 → `terrVegHealth` "healthy" (3) and `terrQuality` **restored** from `terrPreBeetleDamageQuality`. Only countdown (`≥1`) territories are touched, so never-damaged territories (counter 0) are left alone — avoids overwriting their quality with a zero pre-damage value.
- **`LBVIEnvironment.java`:**
  - New input path `beetleImpactFilePath` (empty default = feature OFF, so baseline runs are unchanged; added to `isInputPathParam` so it round-trips in the param CSV). New `loadBeetleImpact()` reads the PSHB scenario CSV (quoted header `runNum,hit.year,hit.terr`; BOM-tolerant, header-validated), keeps only rows for `mpBeetleRunNum`, offset-maps `hit.year` to simulation year via `simYear = hit.year − mpBeetleStartYear + 1` (rows before year 1 skipped), warns+skips unknown terrIDs, and fills `beetleImpactByYear` (sim-year → terrIDs). Missing/blank file = no impacts (optional input).
  - New params: `mpBeetleImpact` (boolean master switch, default **false** = feature off, baseline runs unchanged), `mpBeetleRunNum` (default 1 — which of the 100 PSHB replicates to apply) and `mpBeetleStartYear` (default 1985 — the `hit.year` that maps to sim year 1); `mpBeetleDamageCounter` kept (default 4). All `mp*` so they auto-write to `paramList.csv` and are loadable; `isMpBeetleImpact`/get/set added for the GUI. `beetleImpactFilePath` default is now `RESET_LBVI_inputData/inBeetleImpact_Pendleton.csv` (only used when `mpBeetleImpact` is true).
  - Load (input 6) and scheduling (step 13) of `ActBeetleImpact` are both gated on `mpBeetleImpact`; when off, nothing is loaded or scheduled. The param file is read first, so the switch and the two mapping params are set before the gate.
- **`LBVIObserver.java`:** calls `ActBeetleImpact.recoverFromBeetleDamage` after `collectPopData` at day 363.
- **Scenario files:** three in `RESET_LBVI_inputData/` — `inBeetleImpact_Pendleton.csv`, `inBeetleImpact_SantaClaraCentral.csv`, `inBeetleImpact_SantaClaraEast.csv`. Select a scenario by setting `beetleImpactFilePath` in the param CSV. terrIDs overlap `inTerritories2020.csv` 99–100%.
- **Tests:** `ActBeetleImpactTest` (5 cases: runNum filter + year offset + unknown-terr skip, blank-path off, damage mutation, recovery countdown/restore, undamaged-left-alone). 22/22 pass, compiles clean.

### Team-discussion note — runNum selection (Claude's recommendation differs from the initial lean)
The team wants 100 LBVI runs over the 100 PSHB replicates, and leaned toward deriving `runNum` from the LBVI run seed. **Claude implemented an explicit `mpBeetleRunNum` parameter instead** (set per run, e.g. 1..100 by the planned sweep runner), because it is reproducible and keeps the beetle replicate decoupled from the RNG seed (seed drives LBVI stochasticity; `mpBeetleRunNum` picks the PSHB replicate independently; seed-mod-100 gives arbitrary, hard-to-reproduce pairings). Trivially switchable to seed-derivation if the team prefers — flag for discussion.

## 2026-11-24 — Bug fix: strip UTF-8 BOM on the first line of every CSV input loader

A colleague's run crashed with `NullPointerException` at `LBVIEnvironment.loadVegAttributes` (`col.get("TerrID")` returned null) when loading `inTerritories2020SERDP.csv`. Cause: the CSV was saved (from Excel/Windows) with a UTF-8 BOM (`EF BB BF`), so the first header name parsed as `﻿TerrID`. `String.trim()` does not remove U+FEFF, so the BOM stayed glued to the first column name and the name lookup for that column (here `TerrID`, the first column in that file) failed — PatchID etc. matched because they were not first. Because colleagues hand-make their own CSVs on Windows, this was fixed for **all** input loaders, not just the one that crashed.

- **`LBVIEnvironment.java`:** added a null-safe `stripBom(String)` helper (removes a single leading U+FEFF) and ran the first line of every CSV input loader through it: `loadVegAttributes`, `loadGroundwaterData`, `loadTrapPlan`, `loadInitVireoPopulation`, and `loadParameterList` (strips the BOM off the first parameter name so a BOM'd param file still applies). Removed the two earlier ad-hoc BOM handlings (`loadVegAttributes` per-token `replace`, `loadInitVireoPopulation` `startsWith`/`substring`) in favor of the shared helper so all loaders behave identically. `loadVegetationShapefile` reads the shapefile via GeoTools, not a CSV, so it is unaffected.
- **Data (not code):** removed the BOM from three input CSVs in `RESET_LBVI_inputData/SERDPextent/` — `inTerritories2020SERDP.csv`, `inTerritories2020noWTPSERDP.csv`, `inVireoPopulation2020SERDP.csv` (all had the BOM on a leading `TerrID` column). With the code fix this is no longer required for the model to run, but the files are now clean regardless.
- **Missing-column validation (same change):** added a `requireColumns(col, fileLabel, csvPath, required...)` helper and call it in each column-based loader right after the header map is built — `loadVegAttributes`, `loadGroundwaterData`, `loadTrapPlan`, `loadInitVireoPopulation`. A missing/misspelled/mis-cased required column now throws an `IOException` naming the missing column(s), listing the columns that WERE found, and giving the file path — instead of the old raw `NullPointerException` at a parse line. (`loadParameterList` is `name,value`, not column-based, so it needs no column check.)
- Compiles clean; 17/17 tests pass.

## 2026-11-24 — Feature: input data-file paths loadable from the parameter CSV; `getFileName` honors absolute paths

Colleagues can now set the five input data-file paths from the parameter CSV instead of retyping them in the Model tab each run.

- **`LBVIEnvironment.java`:** added `isInputPathParam(name)` recognizing `shpFilePath`, `vegAttributePath`, `groundwaterFilePath`, `trapPlanFilePath`, `initVireoPopulationFilePath`, and OR'd it into both the `writeParameterList()` keep-rule and the `loadParameterList()` accept-rule. These five fields now appear in the emitted `paramList.csv` and are applied when present in a loaded file. Excluded intentionally: the log OUTPUT files, `paramListFilePath`/`paramFilePath`, and the never-read `tlbFilePath`/`pshbFilePath`. The write happens before start() resolves these paths to absolute, so the snapshot keeps the user's original values and round-trips.
- **`Utils/OutputWriter.java` — `getFileName()`:** an absolute `fileName` is now used as-is; a relative one is resolved against `baseDir` as before. Previously `new File(baseDir, fileName)` mangled an absolute path (Java strips the leading separator from an absolute child), so a pasted full path like `/Users/you/data/in.csv` resolved under the project folder. This lets a colleague point an input path (or any path) anywhere on their machine.
  - **Fix (same day):** two defaults — `shpFilePath` and `vegAttributePath` — were declared with a leading slash (`/RESET_LBVI_inputData/...`) and had relied on the old mangling to be treated as relative; honoring absolute paths broke them (`File not found: /RESET_LBVI_inputData/RESET_merge_final.shp`). Removed the leading slashes (now consistent with the other three input paths), and added a legacy fallback in `getFileName`: an absolute-looking INPUT path that does not exist is retried as baseDir-relative, so older param files / saved GUI values with leading-slash paths keep working.
- **`paramFilePath` is now written into the snapshot (write-only).** The emitted `paramList.csv` records which config file drove the run (blank = none). It is added to the writer keep-rule but deliberately NOT to `isInputPathParam`, so it is not loadable: by the time `loadParameterList` reaches that row the file is already open, so setting it would only make the output snapshot misreport the file actually used. The loader skips a `paramFilePath` row quietly (no warning, not counted), so loading an unedited snapshot stays clean.
- **Docs:** `HOW_TO_RUN_WITH_PARAMS.md` gains an "Input data-file paths" subsection (table of the five rows + relative/absolute note + partial-file example).
- Compiles clean; 17/17 tests pass.

## 2026-11-23 — Refactor: remove the now-dead `endOfSeasonDate()` helper; RENEST date re-check kept (team decision)

- **Removed `endOfSeasonDate()`** (`LBVIAgent.java`) — it returned `mpLastPossibleNestingDate + incubation + nestling + fledgling` (=219 by default). After the end-of-season/mate-release rework, every former caller was changed to `mpLastPossibleNestingDate` or replaced by the female-ends-mate mechanism, leaving this helper with zero callers. Deleted the method and its javadoc. No behavior change.
- **RENEST date re-check — decision: keep per-day (no code change).** The team chose to keep the current behavior where RENEST re-checks the date against `mpLastPossibleNestingDate` on every countdown day (so a renest countdown that would finish past the cutoff is stopped), rather than the literal ODD "check once at the triggering event." The code already does this; the ODD text should be updated to say the renest is re-evaluated against the date each day (see ODD_correction 2026-11-23).
- **Files:** `src/main/java/lbvi/LBVIAgent.java`.
- Compiles clean; 17/17 tests pass.

## 2026-11-23 — Refactor: remove the unused `vireoCurrentNumAttempts` field

Deleted `vireoCurrentNumAttempts` (a per-female count of SUCCESSFUL nesting attempts, incremented at fledgling independence). After the max-attempts trigger was wired onto `vireoNumNestAttempts`, this field drove no logic, was never written to any output file, and duplicated the global `nSuccNests` success counter. Removed the declaration, the constructor init, the increment (and its two dangling `//male's…` comments), and the annual reset; trimmed the stale reference in the RENEST comment.

- **Files:** `src/main/java/lbvi/LBVIAgent.java`.
- **Effect:** none on behavior or output (the field was dead). Compiles clean; 17/17 tests pass.

## 2026-11-23 — Change: wire the max-nesting-attempts end-of-season trigger (ODD actEndBreedingSeason trigger b)

The ODD gives two triggers for actEndBreedingSeason: the date, and *"any Vireo whose vireoNumNestingAttempts == mpVireoMaxNumAttempts."* The second trigger was not actually wired — it appeared only in the NONBREEDING reset guard, but no stage transition routed a max-attempts female into NONBREEDING, so a female who used all her attempts before the date cutoff just kept re-nesting. Now wired.

- **RENEST (`LBVIAgent.java`):** the end-of-season test now also fires when `vireoNumNestAttempts >= mpMaxNumAttempts`, so a female ends her season as soon as her last allowed attempt concludes (she no longer starts attempt max+1).
- **NONBREEDING reset guard:** switched the attempts clause from `vireoCurrentNumAttempts` to `vireoNumNestAttempts` (the same counter RENEST now routes on), so a female sent to NONBREEDING by the attempts trigger *before* the date cutoff actually runs her reset instead of idling until the date.
- **Counter choice — `vireoNumNestAttempts`, not `vireoCurrentNumAttempts`:** `vireoNumNestAttempts` increments at clutch completion (every attempt that reaches incubation, success or failure; false starts excluded), which matches the ODD's "nesting attempts" and its variable name `vireoNumNestingAttempts`. `vireoCurrentNumAttempts` increments only at fledgling independence (successful attempts only) — a max cap on it would almost never fire. **[Pending: confirm with Casey — the field carried a "double check ODD with Casey" note. If the team means successful attempts instead, it's a one-line swap at both sites.]**
- **Side effect:** `vireoCurrentNumAttempts` now drives no logic (still incremented on success and reset each year, but used in no decision). Left in place; it may still be wanted for logging/analysis. Flag if it should be removed.
- **Files:** `src/main/java/lbvi/LBVIAgent.java` (RENEST condition + NONBREEDING guard).
- **Effect:** females now stop breeding after `mpMaxNumAttempts` completed nesting attempts even if the date cutoff hasn't arrived. Compiles clean; 17/17 tests pass. Recommend a validation run checking that few/no females exceed `mpMaxNumAttempts` clutch completions per year.

## 2026-11-23 — Change: RENEST (actEvaluateRenesting) brought in line with the ODD — boundary operator, countdown timing, and false-start routing

Three corrections in the RENEST stage (`LBVIAgent.java`) to match the ODD's actEvaluateRenesting description.

1. **Boundary operator `>=` → `>`.** The ODD ends breeding only when today is *strictly greater than* `mpLastPossibleNestingDate` (renest is allowed when today `<=` it). The code used `>=`, which ended breeding one day early — on day == `mpLastPossibleNestingDate` it stopped the female although the ODD still permits a nest to start on that last possible day. (This is deliberately different from the DISPERSAL/pairing gate, which stays `>=`: "paired *by*" the deadline means an unpaired bird ends *at* it.)

2. **Renesting countdown off-by-one.** The ODD decrements the countdown each day and starts nest building *on the day it reaches 0*. The old code only transitioned the step *after* it hit 0 (decrement in the `>0` branch, transition in a later step), so the effective wait was `mpRenestingIntervalDuration + 1` days. Restructured to decrement then test `<= 0` in the same step, so the wait is exactly `mpRenestingIntervalDuration` days.

3. **False start now routes through renest evaluation (ODD trigger #3).** The ODD lists "abandoned during nest building (false start)" as a condition that triggers actEvaluateRenesting. Previously a false start kept the female in NESTBUILDING and she rebuilt immediately the next day, with no date check and no interval. Now a false start sets `nestCountdownToRenesting = mpRenestingIntervalDuration` and moves her to RENEST, so she re-checks the date (and ends breeding if past the last possible nesting date) and waits the renesting interval before building afresh. Unchanged and still correct: a false start does **not** increment the breeding-attempt counter.

- **Files:** `src/main/java/lbvi/LBVIAgent.java` (RENEST case; nestBuilding false-start branch; two stale docstrings on `nestBuilding`/`abandonNestBeforeEggLaying`).
- **Effect:** renesting timing now matches the ODD (one fewer wait day; renest allowed on the boundary day); a false start now costs a renest-interval delay and is subject to the end-of-season cutoff, instead of an immediate free rebuild. Compiles clean; 17/17 tests pass.
- **KNOWN REMAINING MISMATCH (not changed, needs a team decision):** the RENEST case re-checks the date on *every* countdown day, whereas the ODD checks it *once* at the triggering event. So a renest countdown that starts on/before `mpLastPossibleNestingDate` but would finish after it gets cut short here (she ends breeding), while the ODD would let her complete the rebuild. Only matters for triggers within ~one renest-interval of the boundary. See ODD_correction 2026-11-23.

## 2026-11-23 — Change: a female now ends her mate's breeding season (ODD actEndBreedingSeason), replacing the paired male's date gate

Per the ODD — *"Each Vireo that completes the actEndBreedingSeason action schedules herself, and her mate, to execute… and releases the male from her control"* — a paired male's breeding season is now ended by his **mate**, not by a self-terminating date gate.

- **New `endMateBreedingSeason(state)`** (`LBVIAgent.java`): when a **female** runs her end-of-season reset, she sends her current mate into `NONBREEDING` so he runs his own reset on his next step ("the next day"). No-op for a male caller, an unmated female, a mate that already left the simulation, or a mate that already reset this year (guard against clobbering a male already reset forward to `ARRIVAL`).
- **Called at the top of the female's NONBREEDING reset block**, *before* her own old-age/winter-survival draw, so the mate is released even if she does not survive the winter.
- **Removed the paired male's self-terminating date gate in NESTBUILDING** (`:263`). A paired male now simply parks in NESTBUILDING until his mate ends him — which also correctly covers a nest running past `mpLastPossibleNestingDate` (she ends him only once her own nesting finishes), instead of a fixed day-219 cutoff.
- **Kept the unpaired male's PAIR gate** (`:252`, `mpLastPossibleNestingDate`): a male in PAIR is by definition unpaired (a female who pairs with him moves him to NESTBUILDING), so no female will ever end him — he still self-terminates at the ODD pairing deadline. This preserves the original "Bug A" fix (males no longer freeze) by a different, ODD-faithful mechanism.
- **Side effect:** `endOfSeasonDate()` (the 219 = `mpLastPossibleNestingDate` + full nesting cycle helper) now has **no callers** and is dead code. Left in place for now pending a decision to remove or comment it out.
- **Files:** `src/main/java/lbvi/LBVIAgent.java`.
- **Effect:** paired males end their season exactly when their mate does (tracking a late nest), rather than on a fixed date; unpaired males/females still end at `mpLastPossibleNestingDate`. Compiles clean; 17/17 tests pass. Recommend a validation run watching that no male freezes in NESTBUILDING and that male/female end-of-season resets line up.

## 2026-11-23 — Change: simplify ArrivalDate — sample the arrival date directly from the triangular distribution (drop the Poisson layer)

`ArrivalDate.arrivalDate(lower, mode, upper)` previously drew a value from a triangular distribution and used it only as the lambda (mean) of a Poisson distribution, returning a Poisson draw. The team decided the extra Poisson step is unnecessary, so the arrival date is now the **triangular draw itself**, rounded to the nearest whole day.

- Same mean, less noise: Triangular(a,c,b) already has mean `(a+c+b)/3`, so the Poisson layer never changed the expected date — it only added dispersion and could push dates outside `[a, b]`. The direct draw keeps the mean and guarantees the date stays within `[lower, upper]`.
- **Contract unchanged:** same signature and return type; the `TriangularDistribution` constructor still validates argument ordering (same `NumberIsTooSmall/TooLargeException`s). All 4 existing `testArrivalDate` tests pass unchanged (non-negative, empirical mean ≈ `(a+c+b)/3` within ±3, invalid-ordering throws, CSV sample dump).
- **Old method kept, commented out.** Per request, the previous two-step (triangular→Poisson) method is preserved as a comment block at the bottom of `ArrivalDate.java`, not deleted, so the team can restore it. The `PoissonDistribution` import is retained (with a note) for that reason. Note: if restored, it has the same signature as the active method, so one of the two must be removed/renamed to avoid a duplicate-method error.
- **Files:** `src/main/java/lbvi/Utils/ArrivalDate.java` (method + class docstring).
- **Effect:** arrival dates are now exactly within `[lower, upper]` and slightly less spread out than before (the Poisson over-dispersion is gone); the average arrival timing is unchanged. Compiles clean; 17/17 tests pass.

## 2026-11-23 — Bug fix: a simulation-born bird's FIRST dispersal now uses the juvenile (natal) kernel/label

Until now the juvenile dispersal kernels were never applied to any bird. A newborn sits out its natal year in NONBREEDING (Bug C fix) and the winter reset ages it into the **adult** age class before it ever disperses; since `dispersalKernel` and the dispersal-distance log both branched on `vireoAgeClassAdult`, every home-grown bird dispersed as an adult and the female-Weibull / male-2Dt(3.33,2.41) juvenile kernels sat unused.

Fix: a one-shot per-agent marker `natalDispersalPending` makes a **simulation-born** bird's **first realized dispersal** use the juvenile kernel and be logged as `Juvenile`, without altering its actual age class (survival, breeding, aging stay adult; `AgeYears` still reports the true age — so a natal disperser logs `AgeYears=1, AgeClass=Juvenile`).

- **Scope (team decision):** only the *first* recorded dispersal is juvenile. The marker is consumed when that dispersal is written to `logDispersalDistance.csv`, so any **later** dispersal — a re-dispersal the same season after a failed pairing, or any dispersal in a later year — uses the **adult** kernel/label. (`logDispersalDistance` fires only on a realized settle, so the search-retries while finding that first territory all remain juvenile; the flip happens at the first realized dispersal.)
- **Input (startup) birds are never affected** (marker defaults false): they are established adults and skip dispersal in year 1 anyway.
- Applies to **both sexes** (a male's first territory-defence dispersal is juvenile too).
- **Implementation** (`LBVIAgent.java`): new field `natalDispersalPending` (set true in `reproduceAgent` for newborns); `dispersalKernel` computes `adultDispersal = vireoAgeClassAdult && !natalDispersalPending` and branches on it; `logDispersalDistance` labels AgeClass by the same `adultDispersal` and then consumes the marker. The archived `@Deprecated dispersalKernel_old` was left untouched.
- **Effect:** `logDispersalDistance.csv` will now contain `Juvenile` rows (the yearling natal dispersers), and the juvenile kernels are finally exercised. No change to age class, survival, or breeding. Compiles clean; 17/17 tests pass.

## 2026-11-23 — Docs: clarify loadInitVireoPopulation docstring (input CSV lost PopID/IsSelected columns — no code change needed)

The team removed the `PopID` and `IsSelected` columns from `inVireoPopulation2020.csv`. No code change is required: `loadInitVireoPopulation` reads only the `TerrID` column, located by header name, and ignores all other columns regardless of order, so dropping those two columns has no effect.

- Updated the `loadInitVireoPopulation` Javadoc (`LBVIEnvironment.java`), which stale-ly described the file as single-column with a `terrID` column. It now states that only `TerrID` is read (by name), that other columns (PatchID, POINT_X, POINT_Y, Q20, …) are ignored, and that order doesn't matter. Comment-only; no behaviour change.

## 2026-11-23 — Feature: load model parameters from a CSV file (run a configuration without retyping the Model tab)

Adds a way to drive a run from a parameter file instead of entering every `mp*` value by hand in the Model tab. The file uses the **same `parameter,value` format** that `paramList.csv` already emits, so a run's own snapshot round-trips: run once → copy the emitted `paramList.csv` → edit values → feed it back.

- **New optional input field** `paramFilePath` (default `""`) in `LBVIEnvironment`, with `get/setParamFilePath` so it appears in the Model tab. Blank/null = keep the in-code / Model-tab values (no behaviour change for existing runs).
- **New `loadParameterList(String csvPath)`** — the symmetric inverse of `writeParameterList()`. Reads the `parameter,value` CSV (header row optional) and sets each field by reflection. Acceptance mirrors the writer's keep-rule exactly: a row is applied only if the field exists, is non-static, and its name starts with `mp` (or is `lowerArrivalDate`/`upperArrivalDate`/`modeArrivalDate`). The four per-run **drawn** survival probabilities are never set here (they are outputs drawn in `start()` from their hyperparameters, which *are* loadable). Supported types: `double, int, long, boolean, String, double[], int[]`. Unknown names, non-loadable fields, and unparseable values are each skipped with a warning; a missing file warns and falls back to defaults rather than aborting. Prints an `N applied, M skipped` summary.
- **Hook:** called at the very top of `start()`, ahead of the survival draws and output setup, so loaded values take effect everywhere (and the emitted `paramList.csv` reflects what was actually used).
- **New headless entry point** `LBVIEnvironment.main(String[] args)` — runs the model with no GUI: `java lbvi.LBVIEnvironment [paramFile.csv] [seed]`. Steps until the `LBVIObserver` kill boundary, then finishes. The GUI (`LBVIWorldWithUI.main`) is unchanged; a GUI user can instead type a path into the Model tab's `paramFilePath`.
- **Not a parameter sweep.** This is the single-config loader only (one file → one run). A sweep/batch harness (one file → many runs, replicate seeds, per-run output folders) was explicitly deferred; it would be built on top of this loader by calling the headless runner once per configuration.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java` (field + getter/setter + `loadParameterList` and helpers + `start()` hook + `main`).
- **Effect:** no change to default runs (field blank). Compiles clean; 17/17 tests pass. Not yet exercised by an end-to-end headless run (would overwrite working-dir output CSVs) — recommend a short smoke run before relying on it for production.

## 2026-11-23 — Change: "false start" events now logged to logMortalityEvents.csv instead of logSuccessEvents.csv

The team considers a "false start" (a nest abandoned before egg-laying) a loss, not a success, so it now goes to the mortality file. "nest completion" stays in the success file.

- `LBVIAgent.logBuildingEvent` handled both event types and wrote both to `logSuccessWriter`. It now branches: `"false start"` → `logMortalityWriter`; everything else (`"nest completion"`) → `logSuccessWriter`.
- The two files are **not** identically shaped: `logMortalityEvents.csv` has 11 columns (an extra `CowbirdID` after `NestID`), `logSuccessEvents.csv` has 10. A false-start row is written in the 11-column mortality layout with `CowbirdID = "NA"` (a false start involves no cowbird); the completion row keeps the 10-column success layout. So a bare one-line writer swap would have misaligned the columns — the format string was adjusted accordingly.
- **Files:** `src/main/java/lbvi/LBVIAgent.java` (`logBuildingEvent` only).
- **Effect:** `logSuccessEvents.csv` no longer contains `"false start"` rows; they appear in `logMortalityEvents.csv` with `CowbirdID = NA`. Downstream scripts that counted false starts from the success file must read the mortality file instead. Compiles clean; 17/17 tests pass.

## 2026-11-23 — Change: per-class long-distance dispersal probability (replaces the single `mpVireoLongDistanceDispersal`)

The one model parameter `mpVireoLongDistanceDispersal` (prob. of dispersing far beyond the kernel's range; default 0.001) is replaced by four parameters, one per age/sex class, so each class can carry its own rate.

- **New parameters** (in `LBVIEnvironment`):
  - `mpVireoLongDistanceDispersalFJu = 0.001` — female juvenile
  - `mpVireoLongDistanceDispersalFAd = 0.0196` — female adult
  - `mpVireoLongDistanceDispersalMJu = 0.0491` — male juvenile
  - `mpVireoLongDistanceDispersalMAd = 0.0165` — male adult
- The old single field and its getter/setter were removed; each of the four has a public getter/setter (`get/setMpVireoLongDistanceDispersal<Class>`) so it is user-editable from the Model tab. The reflection-based `paramList.csv` picks them up automatically.
- `LBVIAgent.dispersalKernel` already branched on the four classes; each branch's long-distance draw now reads its matching parameter (FAd/MAd/FJu/MJu) instead of the shared one. No other logic changed — the cutoffs (`mpVireoLowerCutoffLDD`/`mpVireoUpperCutoffLDD`) and the regular-distance kernels are untouched.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java` (field + getters/setters), `src/main/java/lbvi/LBVIAgent.java` (4 draws in `dispersalKernel`).
- **Effect:** FJu keeps the prior 0.001; the other three classes now disperse long-distance more often (MJu highest). Compiles clean; 17/17 tests pass.

## 2026-11-23 — Feature: add a Year column to all time-series output files; log CowbirdID on cowbird-caused egg depredation and nest desertion

Two logging additions requested by the team.

**(1) Year column in every output file.** The four time-series logs that lacked a sim-year column now have one, inserted right after the step column (matching `logDebug.csv`'s `currentStep, Year, Date, …` convention). `logReproductivePerformance.csv` (`year`) and `logDebug.csv` (`Year`) already had it; `paramList.csv` is a parameter snapshot (no time dimension) and is unchanged.
- `logMortalityEvents.csv`: header → `step, Year, Date, …`; all four writers updated (parasitism + nestDeath in `Nest`, `logInterannualMortality` + `logDailyMortalityForNest` in `LBVIAgent`).
- `logSuccessEvents.csv`: header → `currentStep, Year, Date, …`; all five writers updated (hatch, fledge, fledgling-independence, building/false-start, clutch-completion).
- `logDispersalDistance.csv`: header → `currentStep, Year, Date, …`; `logDispersalDistance` updated.
- `logCowbirdArrivalOrDeparture.csv`: header → `currentStep, Year, date, …`; `logMovement` (`CowbirdAgent`) updated.
- Value logged is `currentYear` (1-based sim year), read from the canonical clock the timer advanced at ordering -1.

**(2) CowbirdID on cowbird-attributable mortality events.** `"egg depredation-Cowbird"` and `"nest desertion due to cowbird"` now record the responsible Cowbird's ID in the `CowbirdID` column, the same way a `"cowbird parasitism"` event does (previously both logged `"NA"`).
- New field `Nest.parasitizingCowbirdID` (default -1), set to the laying Cowbird's ID in `cowbirdEggLayingInVireoNests`. If a nest is parasitized by more than one Cowbird across days, it holds the latest (eggs are tracked as counts, not individually, so a specific depredated cowbird egg can't be mapped to a specific Cowbird — the most-recent parasitizing Cowbird is the best available attribution).
- `LBVIAgent.logDailyMortalityForNest` gained a 5-arg overload taking a `cowbirdID` String; the 4-arg form delegates with `"NA"`. The `"egg depredation-Cowbird"` call passes `nest.parasitizingCowbirdID`.
- `Nest.nestDeath` logs `parasitizingCowbirdID` when the event is cowbird-caused (`eventType.contains("cowbird")`, i.e. `"nest desertion due to cowbird"`); all other nest deaths (predation, defoliation, TLB, egg-depredation desertion) stay `"NA"`.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java` (4 headers), `src/main/java/lbvi/LBVIAgent.java` (row writers + overload + depredation call), `src/main/java/lbvi/Nest.java` (field + parasitism/nestDeath rows), `src/main/java/lbvi/Cowbird/CowbirdAgent.java` (movement row).
- **Effect:** every per-event/per-year output row is now attributable to a sim year, and cowbird-caused egg loss and desertion are traceable to a Cowbird. Column counts were re-checked against each header. **Downstream parsers must expect the new `Year` column (and the now-populated `CowbirdID` for those two event types).**

## 2026-11-23 — Change: sex-specific territory selection — males defend a territory regardless of female presence

Split `nestTerritorySelection` by sex so that males establish and defend a territory independent of whether a female is present, while females continue to weigh male availability per the selection trait. Previously both sexes ran the identical trait logic, so a male preferentially settled on territories that already held a female — biologically backwards and a cause of inefficient pairing (both sexes chasing each other, needing to converge on the same territory at the same time).

- **Why:** in reality a male finds and defends a territory, then waits for a female to choose him. The old symmetric code (`this.vireoSexFemale ? queryTerrByPotentialMales : queryTerrByPotentialFemales` at the mate-availability traits) had males seeking females, so no stable set of defended territories formed for females to select from.
- **How:** `nestTerritorySelection` now branches on sex. Females run the unchanged trait `switch`. Males run a new `selectMaleDefenceTerritory` that ignores mate (female) availability:
  - Traits 0/2/3 (have a habitat-quality component): defend the best-quality territory, **random tie-break** among equal-top-quality territories.
  - Traits 1 (mate-only) and 4 (random): defend a **random** suitable territory.
  The global same-sex / `terrQuality==0` exclusions in `findPotentialTerritoryList` (`:636-650`) are unchanged, so males still never pick an already-defended (male-occupied) territory. On selection a male claims the territory (`claimTerritory` → `terrMaleID`) and parks in `PAIR` to wait, exactly as before.
- **Files:**
  - `src/main/java/lbvi/LBVIAgent.java` — `nestTerritorySelection` wrapped the existing trait `switch` in an `else` (female-only) branch and added `selectMaleDefenceTerritory(state)` for males; new private method `selectMaleDefenceTerritory`. Shared post-selection block (`claimTerritory`, `logDispersalDistance`, TEMP diagnostic) unchanged. The female switch's `vireoSexFemale ? males : females` ternaries now always resolve to the male-availability query (left as-is to keep the female path untouched; safe to simplify later).
- **Effect:** males form a stable layer of defended territories; dispersing females (who already seek male-occupied territories via `queryTerrByPotentialMales`) land where a male waits and pair immediately. Expected to raise pairing efficiency and reduce the non-breeding-adult surplus. **Team to confirm with a validation run.**

> **⚠️ TEAM-DISCUSSION NOTE (pending) — male behavior under traits 1 and 4.** Per the user's instruction, males under trait 1 (mate-availability only) and trait 4 (random) currently pick a **random** suitable territory, because a defending male has no mate dimension to evaluate. The alternative Claude raised is to have males **always** defend by habitat quality regardless of the selection trait (treating "defend the best territory you can" as sex-invariant). The user is taking this to the team before it is finalized. Switching to always-by-quality is a small change (route traits 1/4 through the same quality branch as 0/2/3). Also pending the team: whether the quality tie-break should stay **random-among-ties** (current) or deterministic `qualityRank.get(0)` as the old trait-0 female logic used.

## 2026-11-22 — Change: DISPERSAL → NESTTERRSELECT → PAIR now resolve in a single day (one time-step) instead of three

Collapsed the three settling stages so an agent disperses, selects a territory, and attempts to pair within one step, rather than taking one day per stage.

- **Why:** the team wants settling to be a single day's activity. Previously each stage was handled in its own `step()`, so a single settling attempt spanned three days, artificially shortening the effective breeding season and slowing male/female matching.
- **How:** `LBVIAgent.step()` now wraps the stage `switch` in a bounded `while (settling)` loop. Only the two forward transitions set `settling = true` — `DISPERSAL → NESTTERRSELECT` (dispersal produced a territory list) and `NESTTERRSELECT → PAIR` (a territory was chosen) — so the loop runs at most DISPERSAL→NESTTERRSELECT→PAIR and then stops. `PAIR` never continues the loop, so: a female who pairs goes to `NESTBUILDING` (done that day); a female who finds no mate falls back to `DISPERSAL` and retries next day; a male who claimed a territory parks in `PAIR` and waits across days. Chose NOT to merge into one stage because a male's `PAIR` is a genuine multi-day waiting state (a single-stage design would have to re-introduce a wait stage anyway) and redefining the `Stage` enum would ripple into the debug logger and every stage `switch`.
- **Design decision (team):** `ARRIVAL` is deliberately left as its own day — it is not chained into `DISPERSAL`. So a bird arrives on its arrival date, then disperses/selects/pairs the following day. (Claude offered an option to fold arrival in too; team chose to keep arrival separate.)
- **Files:**
  - `src/main/java/lbvi/LBVIAgent.java` — `step()` switched from `switch (currentStage)` (a value captured once) to a `while (settling) { switch (this.vireoReproStage) { … } }` that re-reads the live stage each pass; added `settling = (stage == NESTTERRSELECT)` at the end of the `DISPERSAL` case and `settling = (stage == PAIR)` at the end of the `NESTTERRSELECT` case. No stage bodies were otherwise changed; all other (biological-duration) stages still run once per step.
- **Effect:** settling completes in ~1 day instead of ~3, so birds reach `NESTBUILDING` sooner and mate-matching (via `terrMaleID`/`terrFemaleID` in `vegTerrInfo`) converges faster. The matching dynamic itself is unchanged. Per-day retry semantics are preserved (one dispersal attempt per day). **Team to confirm with a validation run** — watch settlement/pairing timing and overall reproductive output.

## 2026-11-22 — Bug fix: year-1 input agents now run the arrival-date algorithm before nesting (fixes inflated year-1 reproductive output)

Startup (year-1 input) agents used to be placed directly in `NESTBUILDING` on day 0, so they bred for the entire year while every subsequent cohort was constrained to start only after the spring arrival window — producing abnormally high reproductive performance in year 1 only.

- **Why:** `initAgents` set the initial pairs' stage to `NESTBUILDING` with a mate and territory, bypassing `ARRIVAL`. With no arrival-date gate they began building nests at day 0 (Jan 1) and had a full, unconstrained season to re-nest, inflating year-1 `NNests`/`NEggs`/`NFledglings`/etc. In all other years the annual reset returns birds to `ARRIVAL`, so they don't start until their drawn arrival date (late March or later).
- **Fix:** the input pair now starts in `ARRIVAL` and runs the normal arrival-date algorithm. Because it already holds its input territory and mate, on arrival it jumps **straight to `NESTBUILDING`**, skipping `DISPERSAL`/`NESTTERRSELECT`/`PAIR` — gated by a new one-time `initialBreeder` flag that is cleared on first arrival, so year 2+ runs the ordinary dispersal/pairing cycle. Nest building starts the step after arrival (the `ARRIVAL` case has already run that step).
- **Files:**
  - `src/main/java/lbvi/LBVIAgent.java` — new field `boolean initialBreeder = false`; `checkArrival` routes `initialBreeder` birds to `NESTBUILDING` (not `DISPERSAL`) on arrival and clears the flag.
  - `src/main/java/lbvi/LBVIEnvironment.java` — `initAgents` now sets the initial male and female to `Stage.ARRIVAL` (was `NESTBUILDING`) and `initialBreeder = true`; mate status and territory assignment unchanged.
- **Effect:** year-1 breeding seasons are constrained to the spring arrival window like every other year, so year-1 reproductive output should fall into line with later years. Male and female of a pair draw independent arrival dates; harmless, since nesting is entirely female-driven and the male only parks in `NESTBUILDING` until season end. No change to territory/mate bookkeeping (initial pairs still skip `PAIR`, exactly as before — they just arrive first). **Team to confirm with a validation run** comparing year-1 vs later-year rows in `logReproductivePerformance.csv`.

## 2026-11-22 — Change: record logReproductivePerformance.csv on the last day of the year (day 363), labelled currentYear; also schedule nests at ordering 1

Moved the annual population summary from the first step of the next year to the last day of the breeding year, and made nest steppables share ordering 1 with all vireo agents.

- **Why (recording timing):** the summary was collected at `getSteps() % 364 == 0` (day 0 of the next year) and back-labelled `currentYear - 1`, which the team found semantically awkward. The reproductive counters are frozen after the last independence event at `endOfSeasonDate = 219` and nothing touches them until the next reset, so recording any time in the static window [~day 220, 363] yields identical counter values. Recording on the last day of the year (day 363) lets us label the row with `currentYear` directly (no off-by-one) while still running after every agent's end-of-season reset — important because `nSingles` is incremented inside that reset (fires ~day 219-220, timing varies by stage path), so sampling earlier (e.g. literally at day 219) would undercount singles. Day 363 is provably after all resets.
- **Why (nest ordering):** `Nest.step()` fires the daily cowbird-arrival trigger, which writes `nCowbirdArrival` / `nParasitism` (observer counters). Scheduling nests at ordering 1 (was the bare form's 0) keeps the observer at ordering 0 ahead of every counter-writer, consistent with the newborn-ordering fix above.
- **Files:**
  - `src/main/java/lbvi/LBVIObserver.java` — collection condition `getSteps() % 364 == 0` (with `> 0` guard) → `getSteps() % 364 == 363` (guard no longer needed, step 0 ≠ 363); `collectPopData` label `currentYear - 1` → `currentYear`; kill threshold `>= years*364` → `>= years*364 - 1` so the final year is still collected on its day-363 step and the run covers exactly steps 0 .. years*364-1 (one fewer trailing step than before — previously it ran one extra step into day 0 of year N+1).
  - `src/main/java/lbvi/LBVIAgent.java` — nest scheduling (line ~814) `scheduleRepeating(newNest)` → `scheduleRepeating(state.schedule.getTime() + 1.0, 1, newNest)`; start time kept at next step (a nest is created mid-run, so EPOCH would be in the past).
- **Effect:** `logReproductivePerformance.csv` rows are now labelled by the year they describe with no off-by-one, and the numbers are identical to before (same counters; `populationSize` is still the overwinter-survivor population). Within a breeding-season step the nest's cowbird trigger now ties with the owning female (both ordering 1) instead of running first — immaterial, since cowbird arrival is already a daily stochastic draw.

## 2026-11-22 — Bug fix: schedule newborn agents at ordering 1 (was 0) so the observer precedes all agents on the year-boundary step

Made every vireo agent share schedule ordering 1 so the observer's annual summary provably runs before any agent acts on the year-boundary step.

- **Why:** `logReproductivePerformance.csv` is written by `LBVIObserver` (ordering 0) when `getSteps() % 364 == 0` — the first step of the next year — and then it zeros the model-level counters. Initial adults are scheduled at ordering 1 (`LBVIEnvironment.java:770,778`), so the observer (0) runs before them. But newborns used the bare `scheduleRepeating(newAgent)`, which defaults to **ordering 0** — a tie with the observer, broken randomly. Today this is harmless (on day 0 a newborn is in NONBREEDING with `currentJulianDay < endOfSeasonDate=219`, so its reset doesn't fire and it touches no counter), but it is an implicit invariant: if an agent ever incremented a counter on day 0, the random tie could miscount the summary.
- **Files:**
  - `src/main/java/lbvi/LBVIAgent.java` — line ~978, newborn scheduling changed from `scheduleRepeating(newAgent)` to `scheduleRepeating(state.schedule.getTime() + 1.0, 1, newAgent)`. Start time kept at `getTime()+1.0` (next step), matching the bare form — a newborn is created mid-run, so scheduling at `EPOCH (0.0)` would be in the past and MASON would throw. Only the ordering changed, 0 → 1.
- **Effect:** the observer at ordering 0 now strictly precedes all vireo agents (initial and newborn) on every step, including the year boundary. No change to the counter totals today; this removes the latent fragility and makes the code match the stated intent in `LBVIEnvironment.java:354`. Note: nest steppables (`LBVIAgent.java:814`) are still scheduled at the bare ordering 0; left unchanged since they do not write the observer's model-level counters.

## 2026-11-21 — Feature: expose dispersal territory-search attempts as a parameter (mpVireoNumSearchAttempts), default 3→20

Replaced the hard-coded search-iteration count in the DISPERSAL stage with a Model-tab parameter, and raised the default from 3 to 20.

- **Why:** when an agent's `potentialTerritoryList` is empty, it runs an expanding-radius search loop (each iteration `j` widens the buffer by `1 + j*mpVireoSearchDistanceCoefficient`). The iteration count was hard-coded `3`, so the search gave up after three radii. The team wants this tunable, and a higher default (20) so agents search farther before failing to settle.
- **Files:**
  - `src/main/java/lbvi/LBVIEnvironment.java` — new parameter `public int mpVireoNumSearchAttempts = 20` with `getMpVireoNumSearchAttempts()`/`setMpVireoNumSearchAttempts()` (Model-tab editable), placed next to `mpVireoSearchDistanceCoefficient`.
  - `src/main/java/lbvi/LBVIAgent.java` — DISPERSAL stage loop now reads `eState.mpVireoNumSearchAttempts` instead of the literal `3`. (Also fixed an incomplete in-progress edit at that line, `j < mp.`, which did not compile.)
- **Effect:** agents attempt up to 20 expanding-radius territory searches per dispersal step by default (was 3); fully user-tunable. More attempts mean agents are more likely to find an eligible territory before the dispersal time-gate, which may raise settlement/pairing rates — worth watching against the non-breeding-adult investigation.

## 2026-11-21 — Feature: optional log-on-change mode for logDebug.csv (debugLogOnChange)

Added a user-selectable debug-logging mode so the per-agent log can be shrunk to just state transitions without losing the ability to trace bugs, while the full per-step firehose stays available.

- **Why:** even after the NONBREEDING-idle skip, the full log writes one row per agent per active step, which is large and slow to analyze. A "log on change" mode collapses runs of steps where only the clock moved into a single row, keeping every agent and every year (so population aggregates and life-histories stay complete) while cutting the file dramatically. Kept as an option — not the default — so existing runs are unaffected unless a user opts in.
- **Files:**
  - `src/main/java/lbvi/LBVIEnvironment.java` — new parameter `public boolean debugLogOnChange = false` with `isDebugLogOnChange()`/`setDebugLogOnChange()` (Model-tab checkbox, next to `debugLog`). `false` = full (one row per active step, current behavior); `true` = compact (row only on state change).
  - `src/main/java/lbvi/LBVIAgent.java`:
    - new field `lastLoggedDebugFields` — the non-time columns of the last row written for this agent (null until first row).
    - `logDebugState()` restructured: builds the state-defining columns (all columns except the time group `currentStep`/`Year`/`Date`) into a `fields` string; in log-on-change mode, returns early when `fields` equals `lastLoggedDebugFields`. The row is then `currentStep,Year,Date,` + `fields`.
    - change-detection compares the full field tuple (stage, location, `potentialTerrCount`, territory male/female IDs, mate ID/status, age class), not just `LBVIStage`, so within-stage signals (a dispersal search's `potentialTerrCount` ticking, a lone male's `terrFemaleID` filling in, juvenile→adult) still produce a row.
    - updated Javadoc.
- **Behavior / semantics:**
  - **Default is `false`** — output is byte-for-byte the current full log until a user flips the checkbox.
  - **Both modes keep the two noise skips** (pre-arrival `ARRIVAL`, post-reset `NONBREEDING` idle); "full" therefore means every *active* step, not a raw unfiltered firehose. In log-on-change mode the NONBREEDING guard is redundant (change-detection collapses the idle anyway) but is retained for clarity.
  - CSV header and column order are identical in both modes, so downstream analysis does not depend on which mode produced the file.
- **ODD:** see `ODD_correction.md` (2026-11-21, "Output files: logDebug.csv log-on-change mode") — the output-file description should note the two modes and that log-on-change rows are transitions, not a complete per-step record.

## 2026-11-20 — Change: add Year column to logDebug.csv and suppress post-reset NONBREEDING idle rows

Shrank the per-agent debug log by dropping the millions of idle "timer ticking" rows, and added a sim-year column for easier analysis.

- **Why:** the debug log wrote one row per agent per time-step (364 steps × 30 years × every agent), dominated by NONBREEDING idle steps where nothing happens. An agent enters NONBREEDING, the winter reset fires once, then it sits there ~200 days writing identical rows. Those rows carry no behavioral information (the reset's effects are already in `logReproductivePerformance.csv` and `logMortalityEvents.csv`), so suppressing them makes the file far smaller and easier to analyze without losing anything meaningful.
- **Files:**
  - `src/main/java/lbvi/LBVIEnvironment.java` — added `"Year"` to `debugHeader` (second column, after `currentStep`).
  - `src/main/java/lbvi/LBVIAgent.java` — `logDebugState()`:
    - new early-return guard `if (vireoReproStage == NONBREEDING && lastReproResetYear >= currentYear) return;` — skips every NONBREEDING step after the winter reset has fired for the current year.
    - added `state.currentYear` to the row (1-based sim year; matches the `currentStep`/`Date` time group) and widened the format string to 14 fields.
    - updated Javadoc header list and documented both skip guards.
- **Effect:** `logDebug.csv` now keeps exactly one NONBREEDING row per agent per year (the reset-transition step), plus all active-stage rows; the pre-arrival (ARRIVAL) skip was already in place. Column order is now `currentStep, Year, Date, vireoID, …`. No change to model dynamics — logging only.
- **ODD:** see `ODD_correction.md` (2026-11-20, "Output files: `logDebug.csv` gains a `Year` column and no longer logs the non-breeding idle") — any ODD description of the output files' columns or of "one row per agent per time-step" is now stale and must be updated.

## 2026-11-20 — Change: draw clutch size at nest completion, not creation (ODD §3.8, step 4)

Moved the clutch-size draw to the moment a nest finishes building, matching ODD §3.8 ("when `nestDaysToCompletion` is 0, it selects … the number of eggs").

- **Why:** §3.8 draws clutch size at completion. Drawing at creation also made every nest — including false-start rebuilds that are abandoned before completion — consume a clutch-size RNG draw, and the annual female reset drew a clutch size that was never used. Both perturbed the random stream and are now eliminated. The clutch size isn't read until egg-laying (after completion), so completion is the correct just-in-time point.
- **Files:** `src/main/java/lbvi/LBVIAgent.java`, `src/main/java/lbvi/Nest.java`
  - `nestBuilding()` — removed the draw from the nest-creation block; added it to the completion branch (`nestDaysToCompletion <= 0`), setting both the female's `nestMaxNVireoEggs` (the egg-laying cap at STEP 4) and `currentNest.nestMaxNVireoEggs` (tracking), right before the `EGGLAYING` transition.
  - `Nest` constructor — dropped the `int nestMaxNVireoEggs` parameter (the value isn't known at construction); `nestMaxNVireoEggs` now initializes to 0 and is set by the female at completion. The nest still tracks the number, just assigned later.
  - `updateLBVIStateVariables()` — the annual-reset draw `nestMaxNVireoEggs = getNestMaxNEggs(state)` is replaced with `= 0` (cleared; re-drawn at each nest completion).
- **Effect:** exactly one clutch-size draw per **completed** nest, at completion; false starts and the annual reset consume none. `getNestMaxNEggs` is now called from a single site (the completion branch).
- **Clutch-size rework status:** steps 1–4 complete (CDF parameter, categorical draw, scalar removed, draw at completion). The §3.8 clutch-size logic now matches the ODD.

## 2026-11-20 — Refactor: remove scalar mpVireoClutchSize; Nest tracks the drawn clutch size (ODD §3.8, step 3)

Removed the obsolete scalar clutch-size parameter now that clutch size comes from the `mpVireoPopClutchSize` CDF, and wired the `Nest` to track the same drawn value.

- **Why:** `mpVireoClutchSize` (old Poisson rate / uniform bound) is superseded by `mpVireoPopClutchSize`. Its only remaining use was the dead `Nest` uniform draw `random.nextInt(mpVireoClutchSize)`, which nothing read and which disagreed with the female's actual clutch size. The team wants the `Nest` to keep tracking its clutch size, so it now receives the female's drawn value instead of rolling its own.
- **Files:**
  - `src/main/java/lbvi/LBVIEnvironment.java` — removed field `mpVireoClutchSize` and its getter/setter. `mpVireoPopClutchSize` stays Model-tab editable via its existing CSV getter/setter (`getMpVireoPopClutchSize`/`setMpVireoPopClutchSize`).
  - `src/main/java/lbvi/Nest.java` — constructor now takes an `int nestMaxNVireoEggs` parameter and stores it, replacing `state.random.nextInt(state.mpVireoClutchSize)`. The nest holds the same clutch size the female drew.
  - `src/main/java/lbvi/LBVIAgent.java` — `nestBuilding` passes `this.nestMaxNVireoEggs` (drawn via `getNestMaxNEggs`, ODD §3.8) into the `Nest` constructor.
- **Effect:** exactly one clutch-size draw per nest (the female's), shared by the female's egg-laying cap (`LBVIAgent:878`) and the `Nest` object — no more second, inconsistent uniform draw. Clutch size is now a required `Nest` constructor argument, so any future `new Nest(...)` must supply it.
- **Still open:** draw **timing** — clutch size is still set at nest **creation**, not at nest **completion** as §3.8 specifies, and the redundant draw at the annual female reset remains.

## 2026-11-20 — Feature: clutch size drawn from empirical CDF mpVireoPopClutchSize (ODD §3.8, steps 1–2)

Switched the number of eggs in a completed nest from a Poisson draw to the empirical cumulative distribution specified in ODD §3.8.

- **Why:** §3.8 draws clutch size from the completed-clutch-size distribution over sizes 2–5, not Poisson(rate=`mpVireoClutchSize`=5). Stored as the **CDF** (the table's "random number is less than" column) per team preference, so the draw is a direct comparison with no running sum.
- **Files:**
  - `src/main/java/lbvi/LBVIEnvironment.java` — new parameter `public double[] mpVireoPopClutchSize = {0.0233, 0.3805, 0.9962, 1.0000}` (a CDF: entry *i* = P(clutch size ≤ *i*+2), so index *i* → *i*+2 eggs, i.e. sizes 2,3,4,5; must be non-decreasing, ending ~1.0). New getter/setter exposes it in the Model tab as editable CSV text, reusing `doubleArrayToCsv`/`csvToDoubleArray` (same pattern as `mpVireoStartPopFemaleAgeDist`).
  - `src/main/java/lbvi/LBVIAgent.java` — rewrote `getNestMaxNEggs()` to draw `U ~ Uniform(0,1)` from the seeded model RNG (`state.random`) and return the first clutch size whose CDF entry exceeds U (`minClutch = 2`, index 0 → 2 eggs), with a fallback to the largest size if U falls past the last entry, and a degenerate guard for an empty vector. Removed the Poisson draw, the `Math.max(1,…)` floor, the stray `System.out.println`, and the now-unused `PoissonDistribution` import. (The old Poisson used Commons Math's own unseeded RNG; the new draw is reproducible under the model seed.)

> **⚠️ RECOMMENDATION FOR TEAM DISCUSSION — store the PMF (column 2), not the CDF (column 3).**
> The current code stores the **CDF** `{0.0233, 0.3805, 0.9962, 1.0000}`. The draw is identical either way, so this is purely about how the parameter is *stored and edited*, not about model behavior. Claude's lean is to store the **PMF** (the raw proportions `{0.0233, 0.3572, 0.6157, 0.0038}`) instead, for two reasons:
> 1. **Interpretability.** The PMF is the actual empirical quantity ("proportion of completed clutches that are size N") — exactly what the TRACE data reports. The CDF is a derived transform, less obvious to anyone reading or editing the Model tab.
> 2. **Edit safety.** A PMF can be normalized by its own sum on use (tolerating weights that sum to ~0.999 from rounding — the same trick the age-distribution parameters already use). A CDF **cannot** be safely normalized: it must be entered monotonically non-decreasing and ending at exactly 1.0, and a typo or out-of-order value breaks the draw silently.
>
> Trade-off the team should weigh: the CDF makes the draw loop marginally simpler (a direct `U < cdf[i]` compare vs. one running-sum line) and mirrors the §3.8 table's third column literally. If the team prefers the PMF, switching back is a one-line field change plus restoring the running sum in `getNestMaxNEggs()`.

- **Not yet changed (later steps / pending decisions):** the scalar `mpVireoClutchSize` is still present — now used only by the duplicate/dead `Nest.nestMaxNVireoEggs` uniform draw (`Nest.java:40`), which nothing reads. Clutch size is still drawn at nest **creation** (`LBVIAgent` `nestBuilding`, `currentNest == null`) and redundantly at the annual female reset, not at nest **completion** as §3.8 specifies. Open follow-ups: remove the scalar + dead `Nest` field, and move the draw to completion.
- **Note:** `paramList.csv` picks up the new `mp*` field automatically (array rendered with `;` separators).

## 2026-11-20 — Bug fix: parent recruit count (vireoNumRecruits) was never incremented (ODD §3.20 J1)

The female state variable `vireoNumRecruits` — "number of babies in current breeding season" — was declared and reset but **never incremented**, so it was always 0. ODD §3.20 requires each offspring to be recorded on its female parent's recruit count.

- **Cause:** `reproduceAgent()` appended the newborn's ID to the parent's `vireoRecruitList` but never bumped `vireoNumRecruits`; and `vireoRecruitList` itself was initialised once in the constructor and never cleared, so it accumulated for the bird's whole life instead of per season.
- **Fix:** `src/main/java/lbvi/LBVIAgent.java`
  - `reproduceAgent()`: `femaleAgent.vireoNumRecruits++;` alongside the existing `vireoRecruitList.add(newbornID)`.
  - `updateLBVIStateVariables()` (female branch): added `vireoRecruitList.clear();` next to the existing `vireoNumRecruits = 0;` so the count and the ID list are both per-season and stay consistent.
- **Effect:** `vireoNumRecruits` now equals the number of offspring a female produced in the current season; `vireoRecruitList` holds exactly those IDs. Both reset at the end-of-season winter reset. Neither is read by any output yet, so this only makes the state variable correct for future use.
- **Semantics note:** this counts recruits **at birth** (fecundity), per the field's in-code definition and the explicit request. ODD §3.20 literally places the increment at juvenile over-winter *survival* (recruits = survivors). See `ODD_correction.md` (related note) — moving to survival-time counting would require a parent reference on each offspring.

## 2026-11-20 — Doc: drafted ODD §3.20 wording correction (juvenile survivors age to adult)

Added `ODD_correction.md` (a running ODD-corrections log) with its first entry proposing a wording fix for ODD §3.20. The section currently says over-winter juvenile survivors are set to `vireoAgeYears = 0` / ageClass "young", which contradicts the implemented (correct) Bug C behavior where survivors age to 1 and become adults so they can breed the next year. The MD proposes replacement bullets; no code change needed.

## 2026-11-20 — Feature: log inter-annual (non-breeding season) Vireo mortality to logMortalityEvents.csv

Over-winter Vireo deaths in `interannualMortality()` are now recorded in `logMortalityEvents.csv`, per ODD §3.20, for both adults and juveniles.

- **Why:** the non-breeding-season survival draw killed birds (`die()`) without logging the event; §3.20 specifies these deaths be recorded in the mortality output.
- **Files:** `src/main/java/lbvi/LBVIAgent.java`
  - New helper `logInterannualMortality(state, eventType, entityType)` writing one row aligned to `logMortalityHeader` (step, Date, EventType, TerrID, VireoID, NestID, CowbirdID, NumIndividual, EntityType, YoungSp).
  - Called in all four branches of `interannualMortality()` **before** `die()`: juveniles (F/M) → `EventType = "juvenile mortality"`, `EntityType = "juvenile vireo"`; adults (F/M) → `EventType = "adult mortality"`, `EntityType = "adult vireo"`. Replaced the two stale `//log the death in logVireoSurvivalOutcomes.csv` placeholder comments.
- **Column fill:** `NumIndividual = 1` (one bird per row), `TerrID = vireoCurrentLocation`, and `NestID = CowbirdID = YoungSp = "NA"` — these deaths are neither nest- nor cowbird-related and involve no nest young (species/stage is already in `EntityType`). Matches the ODD's named fields (VireoID, NumIndiv, EntityType, Date).
- **Note:** logging precedes `die()` so the agent's state (ID, location, age class) is still valid when the row is written.

## 2026-11-19 — Bug fix: regular dispersal distances were 1000× too short (km used as metres)

Realized dispersal distances were far shorter than the empirical kernels (`LBVI_DispKernels4.jpg`). Root cause: a unit mismatch between the kernel draws and the coordinate system.

- **Bug:** the four regular (non-long-distance) kernel draws in `dispersalKernel()` return distances in **kilometres** (IG mu=2.66 km; 2Dt; Weibull), but `findPotentialTerritoryList()` (`LBVIAgent.java` ~:564-565) adds `vireoDispDist` directly to territory coordinates that are in **metres** (`inTerritories2020.csv` POINT_X/POINT_Y, projected metres; adjacent territories ~100 m apart). So an adult female drawing ~2.66 km shifted her search centre only ~2.66 m — she re-settled in her own or an adjacent territory. The realized `Distance` in `logDispersalDistance.csv` (Euclidean metres between territory centres) therefore came out ~1000× too small. Tell-tale inconsistency: the **long-distance** branch in the same method already draws in metres (`mpVireoLowerCutoffLDD=20000`, `mpVireoUpperCutoffLDD=200000`, i.e. 20–200 km), so LDD was correctly scaled while regular dispersal was not.
- **Fix:** multiply each of the four regular kernel draws by `1000.0` (km → m) in `dispersalKernel()` — Female Adult (IG), Male Adult (2Dt), Female Juvenile (Weibull), Male Juvenile (2Dt). The long-distance branches are left unchanged (already metres).
- **Files:** `src/main/java/lbvi/LBVIAgent.java` `dispersalKernel()` (`:527`, `:534`, `:541`, `:548`).
- **Verification:** the `DispersalKernal` samplers were re-checked and are correct — they reproduce the published kernels in km (inverse-CDF math verified for all four; existing `Sample*Test` classes confirm mean/shape). The defect was only in the km→m application, not the samplers. The archived `dispersalKernel_old()` (deprecated, uncalled) also treated the draws as km (its outlier arrays are 24/25/29 km etc.), confirming km is the kernel's native unit.
- **Note:** any prior analysis of realized dispersal distances (and any settlement/pairing behaviour that depends on how far birds can reach) was produced under the 1000×-short gate and should be re-measured on a fresh run.

## 2026-11-19 — Bug fix: DISPERSAL time-gate inequality was inverted and quit the season ~100 days early

The "enough time to nest?" check in `case DISPERSAL` (`LBVIAgent.java` ~:126) used a malformed inequality that pushed dispersing birds into NONBREEDING far too early.

- **Bug:** condition was `currentJulianDay + incubation + nestling + fledgling + renesting > mpLastPossibleNestingDate`. This is algebraically `currentJulianDay > mpLastPossibleNestingDate − (full cycle + renesting)` — it *subtracted* the nesting cycle from the last nest-start date instead of adding it. With defaults (`mpLastPossibleNestingDate=169`, incubation=14, nestling=11, fledgling=25, renesting=2) birds gave up once `currentJulianDay > 117`, truncating the entire breeding season at ~day 118. It also wrongly reserved time for a *re*-nest when deciding whether to disperse for a *first* nest.
- **Fix (by kaiyinlin):** `currentJulianDay > mpLastPossibleNestingDate + incubation + nestling + fledgling` → birds now give up only after the season truly ends (~day 219), and the renesting term is removed.
- **Files:** `src/main/java/lbvi/LBVIAgent.java` `case DISPERSAL`.
- **Recommended follow-up (not yet applied):** replace the inline arithmetic with the existing `endOfSeasonDate(eState)` helper (the new expression equals it exactly) and use `>=` to match the other season-end gates at ~:217/:228/:431 — i.e. `if (eState.currentJulianDay >= endOfSeasonDate(eState))`.
- **Impact on prior analysis:** `ANALYSIS_nonbreeding_adults.md` was produced under the buggy gate (season capped at ~day 118), so its TIMEGATE rate and dispersal→pairing funnel are distorted and should be re-measured on a fresh run. The sex-ratio / male-surplus root cause is survival-driven and independent of this gate, so it still holds.

## 2026-11-19 — Feature: Expose startup age distributions in the MASON Model tab

Added JavaBean getters/setters so `mpVireoStartPopFemaleAgeDist` and `mpVireoStartPopMaleAgeDist` (the Leslie-matrix startup age distributions) are editable from the Console Model tab.

- **Why:** a colleague wants to edit the female/male startup age distributions from the GUI.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java` — added getters/setters for `mpVireoStartPopFemaleAgeDist` and `mpVireoStartPopMaleAgeDist`, placed after `get/setMpProbVireoIsFemale`.
- **Exposed as editable TEXT, not `double[]`:** a raw `double[]` property renders as `double[]@<hash>` in MASON and is not editable, so the getters return a comma-separated String and the setters parse it (helpers `doubleArrayToCsv` / `csvToDoubleArray`). The colleague types weights like `0.4649, 0.2488, 0.1331` (commas/spaces/newlines all accepted). Bad or empty input is rejected and leaves the current distribution unchanged.
- **Note:** the matching max-age fields `mpVireoPopMaxAgeF/M` already had getters/setters (so they were already tab-editable). Keep each distribution's value count aligned with its max-age field — `sampleStartupAge()` draws startup age from the array length while death uses the max-age field, so a count/max-age mismatch yields inconsistent demography (no crash). See `ANALYSIS_nonbreeding_adults.md` for why these distributions matter (sex-differential survival drives the male surplus).

## 2026-11-18 — Change: TEMPORARY logDispersalOutcome.csv diagnostic (to be removed)

Added a temporary `logDispersalOutcome.csv` output to investigate "why are so many adults not breeding?". (Supersedes the same-day stdout `DISPERSAL_OUTCOME` prints — now written to a real CSV via `OutputWriter`, matching the other log files.)

- **Why:** to measure the settle-vs-timegate split at the DISPERSAL exit points. An empty territory list does NOT send a bird to NONBREEDING directly — only the nesting time-gate (~:126) does; birds with no suitable territory bounce in DISPERSAL re-rolling until the clock runs out. This log quantifies how often adults leave DISPERSAL by settling vs. by the time-gate (and, for time-gate exits, whether their candidate list was empty).
- **Files:**
  - `src/main/java/lbvi/LBVIEnvironment.java` — new `logDispersalOutcomeFile` field, `logDispersalOutcomeWriter` field, and init block (header `Year, Date, vireoID, Sex, AgeClass, Outcome, ListWasEmpty`). All tagged `// TEMP DIAGNOSTIC`.
  - `src/main/java/lbvi/LBVIAgent.java` — `// TEMP DIAGNOSTIC` blocks in `case DISPERSAL` (SETTLE/TIMEGATE), `case PAIR` (PAIR_OK/PAIR_FAIL), and at the end of `nestTerritorySelection()` (NESTTERR_PAIR/NESTTERR_REJECT), all writing via `logDispersalOutcomeWriter.addToFile(...)`, guarded by `debugLog`.
- **Output:** `logDispersalOutcome.csv` in the same output folder as the other logs. `Outcome` ∈ {SETTLE, TIMEGATE, NESTTERR_PAIR, NESTTERR_REJECT, PAIR_OK, PAIR_FAIL}. 7th column meaning depends on Outcome: ListWasEmpty (TIMEGATE/SETTLE), candidate-list size (NESTTERR_*), 0 (PAIR_*) — group by Outcome.
- **Why these extra events:** the first run showed ~88% of dispersers DO find a territory list (SETTLE), so discovery isn't the bottleneck. The PAIR/NESTTERR counters measure where settled birds are lost afterward (trait rejection, or female settling with no male present).
- **TEMPORARY:** remove all TEMP blocks (LBVIAgent: DISPERSAL×3, PAIR×2, nestTerritorySelection×1; LBVIEnvironment: field, writer field, init block) once the non-breeding question is resolved. Not a permanent model output.

## 2026-11-18 — Feature: Add Sex, AgeClass, and AgeYears columns to logDispersalDistance.csv

`logDispersalDistance.csv` now records the disperser's sex and age alongside each realized dispersal event.

- **Why:** to let the team analyze dispersal distance by sex and age class (e.g. juvenile vs. adult dispersal).
- **Files:**
  - `src/main/java/lbvi/LBVIEnvironment.java` (~:281) — appended `"Sex"`, `"AgeClass"`, `"AgeYears"` to `logDispersalDistanceHeader`.
  - `src/main/java/lbvi/LBVIAgent.java` `logDispersalDistance()` — appended `vireoSexFemale ? "F" : "M"`, `vireoAgeClassAdult ? "Adult" : "Juvenile"`, and `vireoAgeYears` to the record (matching the existing convention used in the agent's logging at ~:435).
- **Effect:** new columns at the **end** of each row (existing columns keep their positions, so position-based downstream scripts are unaffected). New header: `currentStep, Date, terrID, vireoID, PrevLoc, Distance, Sex, AgeClass, AgeYears`.

## 2026-11-18 — Change: NNests excludes false-start rebuilds; NNests/NPairs counted at nest completion

Decision (team): `NNests` counts every renesting attempt but NOT false-start rebuilds. A false start is abandoned *during building* (before egg-laying), so both `NNests` and `NPairs` are now tallied when a nest **completes building** (advances to `EGGLAYING`) rather than when the `Nest` object is created.

- **Why:** `nNests++` previously fired at nest creation (`nestBuilding`, `currentNest == null` block), so each false-start rebuild added a nest — inflating the count (your year-1 value of 24867). Counting at completion excludes abandoned false starts while still counting every completed attempt, including renests.
- **Files:** `src/main/java/lbvi/LBVIAgent.java` — moved `state.nNests++` and the `hasNestedThisYear`-guarded `nPairs++` out of the nest-creation block and into the `nestDaysToCompletion <= 0` completion branch (right after the "nest completion" log, before the `EGGLAYING` transition).
- **Effect:**
  - `NNests` = total nests that completed building this year (renests included, false starts excluded).
  - `NPairs` = unique females that **completed** at least one nest (consistent with the new nest definition: a female who only ever false-starts no longer counts). Still once-per-female via `hasNestedThisYear`.
- **Note:** the earlier same-day change had these counters at nest *creation*; this supersedes that location. False-start events are still logged to `logSuccessEvents.csv` ("false start"), just not counted as nests.

## 2026-11-18 — Change: NPairs now counts unique nesting females (not PAIR transitions)

`NPairs` in `logReproductivePerformance.csv` is redefined to match the ODD ("number of unique female Vireos that created at least one nest").

- **Why:** it was incremented at the `PAIR` stage transition, which (a) missed the initial pairs — they start in `NESTBUILDING` at init and never pass `PAIR`, so year 1 reported 1218 instead of ~4537; (b) counted females that paired but never nested; and (c) could double-count a female who re-paired after a mate died.
- **Files:** `src/main/java/lbvi/LBVIAgent.java`
  - New per-female field `hasNestedThisYear` (initialised false in the constructor, cleared in `updateLBVIStateVariables`).
  - Removed `nPairs++` from the `PAIR` case.
  - In `nestBuilding`, when a female creates a nest, `if (!hasNestedThisYear) { nPairs++; hasNestedThisYear = true; }` — counts each female once, on her first nest of the season.
- **Effect:** `NPairs` now equals the number of unique females that nested that year, including the initial pairs, with no double-counting. The year-0 row still logs the starting pair count directly (`initVireoTerrIDs.size()`), unaffected.
- **Note:** nest creation currently fires on every `Nest` object including false-start rebuilds, but the `hasNestedThisYear` guard means that affects `NPairs` only through the very first nest, so the false-start question (still pending) does not change `NPairs`. `NNests` is intentionally left unchanged until that question is settled.

## 2026-11-18 — Bug fix: annual lifecycle broken after year 1 (males froze, female reset too late, juveniles bred in natal year)

After year 1, pairing collapsed (NumPair/NumNests = 0 from year 2) and year-1 counters were nonsensical. Root cause was three independent lifecycle bugs in `src/main/java/lbvi/LBVIAgent.java`. Diagnosed from `logReproductivePerformance.csv` + `logDebug.csv`; see memory `lifecycle-three-bugs`.

- **Bug A — males never reset (froze permanently).** The `PAIR` and `NESTBUILDING` stage cases only acted `if (vireoSexFemale)`, so a male (parked in `PAIR`, or pushed into `NESTBUILDING` when his mate paired) never transitioned again. The annual reset is only reachable via `NONBREEDING`, which males never reached — in year 2, 5562 males were frozen in NESTBUILDING + 631 in PAIR.
  - **Fix:** added an `else` branch to both cases: a male with `currentJulianDay >= endOfSeasonDate(state)` advances to `NONBREEDING` (then resets normally).
- **Bug B — female annual reset fired too late to re-enter the next spring.** The `NONBREEDING` reset (~day 219) sent birds to `ARRIVAL`; `checkArrival` advanced as soon as `currentJulianDay >= arrivalDate`, which at day 219 was already true → immediate `DISPERSAL` → dispersal time-gate → bounce back to `NONBREEDING` for good. Birds never reached `ARRIVAL` during the real spring window again.
  - **Fix:** `checkArrival` now only advances within the arrival window: `currentJulianDay >= vireoArrivalDate && currentJulianDay <= upperArrivalDate`. A reset bird waits at `ARRIVAL` until `currentJulianDay` wraps to 0 at the year boundary and arrives normally the next spring.
- **Bug C — newborns bred in their natal year.** `reproduceAgent` created each recruit as a juvenile starting in `Stage.ARRIVAL` and scheduled it immediately, with no adult/next-year gate; early-born recruits dispersed, paired and nested the same season (the 1218 year-1 "pairs" were juveniles, IDs > 9074, not initial pairs re-pairing).
  - **Fix:** recruits now start in `Stage.NONBREEDING`; they sit out the natal season, and the end-of-season reset draws juvenile winter survival, ages them to adult, and returns them to `ARRIVAL` for next year.
- **Supporting changes:**
  - New helper `endOfSeasonDate(state)` = `mpLastPossibleNestingDate + mpIncubationStageDuration + mpNestlingStageDuration + mpFledglingStageDuration` (replaces the inline local in the `NONBREEDING` case; reused by the Bug A fix).
  - `nSingles` is now incremented only for breeding-eligible adults (`!vireoMateStatus && vireoAgeClassAdult`), so natal-year juveniles parked in `NONBREEDING` (Bug C) are not miscounted as singles.
- **Not changed yet (separate issue):** counter *definitions* still need revisiting — `nPairs` is incremented only at the `PAIR` transition, so the initial 4537 pairs (which start in `NESTBUILDING` at init and never pass `PAIR`) are still uncounted in year 1; `nNests` counts every nest incl. renests and false-start rebuilds (pending `open-q-nnests-false-starts`). Validate these after confirming birds now breed every year.

## 2026-11-17 — Bug fix: NumCowbirdCaptured always 0 (trap year mismatch + multi-year trap data dropped)

Cowbird captures never occurred, so `NumCowbirdCaptured` in `logReproductivePerformance.csv` was always 0.

- **Cause (two bugs):**
  1. **Year-scale mismatch:** `findOpenTraps()` tested `trap.year == state.currentYear`, but `trap.year` is a calendar year (trap CSV `Year` = 2025–2064) while `currentYear` is 1-based (1…30). The comparison was never true, so `findOpenTraps()` always returned an empty list and no capture draw ever ran.
  2. **Multi-year trap data collapsed:** `trapInfo` was a `Map<Integer trapID, TrapInfoIndentifier>`, but the trap CSV has 272 traps × 40 yearly rows reusing the same TrapIDs. `put()` overwrote, so only the last year read (2064) survived — 39/40 years lost.
- **Fix:**
  - `src/main/java/lbvi/LBVIEnvironment.java`
    - New user parameter `mpStartCalendarYear` (default **2025**) with getter/setter, so users can anchor simulation year 1 to any calendar year (2025, 2026, …). Shown in the Model tab.
    - New helper `currentCalendarYear()` = `mpStartCalendarYear + (currentYear - 1)` (not a getter, so not a Model-tab property).
    - `trapInfo` changed from `Map<Integer, TrapInfoIndentifier>` to `List<TrapInfoIndentifier>`; `loadTrapPlan()` now `add()`s one entry per CSV row, retaining every trap-year.
  - `src/main/java/lbvi/Cowbird/logisticFunForCapture.java` — `findOpenTraps()` iterates the list and matches `trap.year == state.currentCalendarYear()`.
- **Effect:** with the default `mpStartCalendarYear = 2025`, sim year 1 → 2025 … year 40 → 2064, matching the trap plan; traps now open in their intended years and captures occur. Capture probability logic (`computeCaptureProb`, distance gate `mpMaxCowbirdCaptureDistM`) was already correct and is unchanged.
- **Out of scope:** groundwater loading has the analogous `Map`-keyed-by-patchID collapse and no year mapping, but it is slated for removal from the model, so it was intentionally left unchanged.

## 2026-11-17 — Refactor: decouple the model from the shapefile (fixes dropped initial territories)

The shapefile (`RESET_merge_final.shp`) is now used for UI/display only (background map image + world bounds); it no longer gates which territories exist in the model or where agents are placed. Territory grid positions are derived from the vegetation CSV's `POINT_X`/`POINT_Y` instead of shapefile centroids.

- **Why:** the shapefile was silently dropping territories absent from it. With `inVireoPopulation2020.csv` (initial population), 443 rows / 397 unique TerrIDs were skipped because they had no shapefile polygon, so year 0 reported ~4100 pairs instead of the full count — even though all those territories were present in `inTerritories2020.csv`, which is the data the ecological model (quality, dispersal, mate availability) actually uses. The shapefile's polygon geometry is never read by model logic; grid cells were its only model-facing contribution, and those can come from the veg CSV.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java`
  - `loadVegetationShapefile()` (`:396`): removed `territoryLocations.put(...)` — the shapefile no longer sets agent grid positions. It still provides the background image, world bounds (`shpMinX..shpMaxY`, used by `worldXToGrid`/`worldYToGrid`), and `terrGeometries` (display-only, unused by logic).
  - `loadVegAttributes()` (`:453`): removed the "no shapefile entry → skip" guard; grid position is now computed as `new Int2D(worldXToGrid(coordX), worldYToGrid(coordY))` from the CSV's POINT_X/POINT_Y and stored in `territoryLocations`. Geometry may be null for territories absent from the shapefile (fine — never read).
  - `loadInitVireoPopulation()` (`:615`): the skip guard now checks `vegTerrInfo` (the ecological data) instead of `territoryLocations`/shapefile membership, so a bird is kept iff its territory exists in `inTerritories2020.csv`.
- **Effect:** every initial territory present in the vegetation CSV is now modeled and placed; year-0 `nPairs` equals the number of initial-population rows (one pair per row). Population count (`vegetationGrid.getAllObjects().numObjs`) is correct because all agents are placed. Load order (shapefile → veg attrs → init population) already satisfies the new `vegTerrInfo` dependency.
- **Note:** the current `inVireoPopulation2020.csv` has 4537 rows / 4537 unique TerrIDs (no duplicates), all present in `inTerritories2020.csv`, so year-0 pairs = 4537.

## 2026-11-17 — Feature: mpTerrSelectionTrait shown as a labeled dropdown in the Model tab

The territory-selection-trait parameter now appears in the MASON Console Model tab as a dropdown of readable labels instead of a bare integer input.

- **Why:** users could not tell what the integer codes 0–4 meant; the labels make the choices self-explanatory and prevent out-of-range entries.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java` — added `domMpTerrSelectionTrait()` (after the getter/setter, `:892`) returning a `String[]` of the five labels ("0: habitat quality", "1: mate availability", "2: habitat then mate availability", "3: mate availability then habitat", "4: completely random").
- **Note:** MASON's domain mechanism keeps the property an `int`; the selected list index (0–4) is the stored value, so the labels map directly to the existing codes and model logic is unchanged.

## 2026-11-17 — Change: end-of-season reset date + hide auto-drawn survival probabilities

Two related model-panel/behaviour changes plus the follow-up needed to keep the four survival probabilities out of every output.

- **Change A — end-of-season winter-reset date (`src/main/java/lbvi/LBVIAgent.java`, `case NONBREEDING` `:377`):** the trigger date was extended from `mpLastPossibleNestingDate` to `endOfSeasonDate = mpLastPossibleNestingDate + mpIncubationStageDuration + mpNestlingStageDuration + mpFledglingStageDuration`. The winter reset (`nSingles` count, aging, `interannualMortality()`) now fires once the last-started nest has had time to run through incubation, nestling, and fledgling stages, rather than at the last possible *start* date. The once-per-agent-per-year `lastReproResetYear` guard is unchanged.
  - **Why:** birds that started a nest near `mpLastPossibleNestingDate` need the full nesting duration to finish before the season is treated as over.
- **Change B — hide the four auto-drawn survival probabilities from users (`src/main/java/lbvi/LBVIEnvironment.java`):** `mpSurvivalProbJuvenileM`, `mpSurvivalProbJuvenileF`, `mpSurvivalProbAdultM`, `mpSurvivalProbAdultF` are drawn per run in `start()` from their hyperparameters, so users should not define them.
  - Model tab GUI: their getters/setters remain commented out (`:969`–`:979`), so they do not appear in the Console inspector.
  - `paramList.csv`: `writeParameterList()` (`:520`) is reflection-based on `mp*` field names and so would still have written all four. Added a `drawnSurvivalProb` exclusion that skips exactly those four field names; their hyperparameters (`…_gammaShape/_gammaRate`, `mpSurvivalProbAdultM_betaAlpha/_betaBeta`) are still captured. Doc comment updated.
  - **Why:** they are outputs, not inputs, and are reconstructable from the hyperparameters + run seed, so excluding them loses nothing for reproducibility.
- **Note:** the per-run draws are still echoed to stdout by the `"Per-run survival probs: …"` line in `start()`; that is unchanged.

## 2026-11-17 — Change: female-adult survival now drawn from a Gamma distribution

The per-run survival-probability draw for adult females (`mpSurvivalProbAdultF`) was switched from a Beta to a Gamma distribution, parameterised `shape = 306.33`, `rate = 514.69` (mean ≈ 0.595). The old Beta hyperparameters (α = 132.94, β = 34.55, mean ≈ 0.794) were incorrect.

- **Why:** the Beta parameterisation for female-adult survival was wrong; corrected to the intended Gamma.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java`
  - Field (`:178`): renamed `mpSurvivalProbAdultF_betaAlpha/_betaBeta` → `mpSurvivalProbAdultF_gammaShape = 306.33` / `mpSurvivalProbAdultF_gammaRate = 514.69`.
  - Draw in `start()` (`:247`): now `Math.min(1.0, new GammaDistribution(rng, shape, 1.0/rate, …).sample())`, matching the juvenile Gamma draws (Gamma is unbounded above, so the `[0,1]` clamp is applied).
  - Getter/setter (`:1000`): renamed to `get/setMpSurvivalProbAdultF_gammaShape` and `…_gammaRate` (appear in the Model tab).
- **Note:** male-adult survival is unchanged (still Beta, α = 132.94, β = 34.55). The reflection-based `paramList.csv` snapshot now logs the new `_gammaShape`/`_gammaRate` names automatically.

## 2026-11-16 — Feature: log cowbird parasitism events to logMortalityEvents.csv

Each time a Cowbird lays an egg in a Vireo nest, a mortality event of `EventType = "cowbird parasitism"` is now written to `logMortalityEvents.csv`, recording the territory, nest, and the Cowbird that laid the egg (model description §3.15).

- **Why:** team wants parasitism events captured in the mortality output.
- **Files:**
  - `src/main/java/lbvi/Nest.java` — `cowbirdEggLayingInVireoNests()` now takes a `cowbirdID` parameter and, after the Cowbird egg is laid (past the `female == null` guard), writes a row: `EventType = "cowbird parasitism"`, `TerrID = nestTerrID`, `VireoID = nestFemaleID`, `NestID = nestID`, `CowbirdID = cowbirdID`, `NumIndividual = 1`, `EntityType = EGG`, `YoungSp = "cowbird"` (aligned to the existing 10-column `logMortalityHeader`).
  - `src/main/java/lbvi/Cowbird/CowbirdAgent.java` — `parasitize()` passes `this.cowbirdID` into the call.
- **Note:** the row is logged only when an egg is actually laid (the early `female == null` return still increments `nParasitism` but lays no egg and logs nothing).

## 2026-11-16 — Bug fix: inflated NumSingles / NumNests (within-year recycling)

`logReproductivePerformance.csv` reported far too many `NumSingles` and `NumNests` relative to `NumPairs`.

- **Cause:** in `LBVIAgent.step()`, the `case NONBREEDING` gate (`:370`) included `|| this.vireoReproStage == Stage.NONBREEDING`, which is always true inside that case. The end-of-season winter reset (`interannualMortality()` + aging + `nSingles++`) therefore fired on *every step* a bird sat in `NONBREEDING`, from the moment it entered — at any day. Each reset sent the bird back to `Stage.ARRIVAL` (`updateLBVIStateVariables`), where it re-drew an early-season arrival date already in the past and immediately re-arrived, re-dispersed, re-paired, and re-nested within the same year. That within-year recycling multiplied the per-year counters (`nSingles`, `nNests`, and also `nPairs`).
- **Fix:** `src/main/java/lbvi/LBVIAgent.java`
  - New per-agent field `lastReproResetYear` (init `-1`).
  - Rewrote the `NONBREEDING` gate to fire the winter reset **once per bird per year**: `(currentJulianDay >= mpLastPossibleNestingDate || vireoCurrentNumAttempts >= mpMaxNumAttempts) && lastReproResetYear < currentYear`, setting `lastReproResetYear = currentYear` when it fires.
  - Kept the `mpMaxNumAttempts` trigger (worn-out birds can enter the winter transition early). The once-per-year guard prevents the previous repeated-reset behavior, double-aging, and birds being stranded in `NONBREEDING`.
- **Note:** `NumPairs` was also inflated by the same recycling, so pre-fix values of all three counters should not be treated as a baseline.

## 2026-11-16 — Feature: year-0 row in logReproductivePerformance.csv

The reproductive-performance log now includes a `year = 0` row for the initial input/starting population, written at simulation start before any steps run.

- **Why:** the starting condition (year 0) was never recorded; only completed years were logged.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java` — `start()`, immediately after `initAgents()`.
  - Row contents: `year = 0`, `populationSize =` all agents on the grid, `nPairs = initVireoTerrIDs.size()` (every initial territory starts paired), all breeding-outcome counters `= 0`.
- **Depends on:** the 2026-11-16 change making the initial population start paired.

## 2026-11-16 — Change: initial population starts paired and nest-building

At simulation start (year 0), the initial vireos now begin already mated and in the nest-building stage, rather than in the arrival stage and unpaired.

- **Why:** the starting population should be established pairs ready to breed at year 0, not newly-arrived birds that still have to pair up.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java` — `initAgents()`
  - Each initial female and male now has `vireoReproStage = Stage.NESTBUILDING` (was arrival stage).
  - Each has `vireoMateStatus = true`.
  - The male and female created for each territory are mated to each other via `vireoMateID` (each points at the other's `vireoID`).
- **Author:** change made by the user.

## 2026-11-16 — Feature: per-run parameter snapshot CSV

Every simulation run now writes `paramList.csv` (a `parameter,value` row for each model parameter) so runs are reproducible and parameter settings are never lost.

- **Why:** collaborators wanted a parameter list saved with every run.
- **Approach:** reflection-based — captures every non-static field whose name starts with `mp` (plus `lowerArrivalDate`, `upperArrivalDate`, `modeArrivalDate`) automatically, so newly added parameters appear with no maintenance.
- **Files:** `src/main/java/lbvi/LBVIEnvironment.java`
  - Added field `paramListFilePath = "paramList.csv"`.
  - Added `writeParameterList(String)` + `formatParamValue(Object)` (before `loadTrapPlan`). Overwrites each run via `FileWriter(path, false)`; arrays rendered with `;` separators; comma/quote values CSV-quoted.
  - Called as "(output 7)" in `start()`, after the other output files.
- **Note:** the snapshot is taken after the per-run survival draws, so it records the *realized* survival values (e.g. `mpSurvivalProbJuvenileM`) used by that run, not just the hyperparameters.
- **Output location:** base directory, alongside the other log CSVs.

## 2026-11-14 — Change: trap open/close dates are now model-wide parameters

Cowbird trap open/close timing moved from per-trap CSV columns to two global model parameters applied to all traps. The unused `NDaysClose`/`NDaysClosed` concept was removed entirely.

- **Why:** the team's new trap input has only 4 columns (`POINT_X, POINT_Y, TrapID, Year`); open/close/NDaysClosed were dropped from the per-trap data. Nothing in the model ever read `NDaysClose`.
- **Files:**
  - `LBVIEnvironment.java` — added `mpOpenDate = 91` and `mpCloseDate = 169`; `loadTrapPlan()` now reads 4 columns and calls the 4-arg constructor.
  - `Cowbird/logisticFunForCapture.java` — `findOpenTraps()` now tests against `state.mpOpenDate`/`state.mpCloseDate` instead of per-trap dates. (Only logic that reads open/close.)
  - `Traps/TrapInfoIndentifier.java` — removed `trapOpenDate`, `trapCloseDate`, `trapNDaysClose`; constructor is now `(POINT_X, POINT_Y, trapID, year)`.
- **Input CSV:** `RESET_LBVI_inputData/inCowbirdTrapsRecent2020.csv` (header `POINT_X,POINT_Y,TrapID,Year`; Year values are 2025 despite the filename).
