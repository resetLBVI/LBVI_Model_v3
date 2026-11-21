# Analysis: Why are so many adults not breeding?

**Date:** 2026-11-18
**Author:** kaiyinlin (with Claude Code)
**Status:** Investigation in progress. Based on a temporary diagnostic log (`logDispersalOutcome.csv`) added for this question — see `CHANGELOG.md` and remove the `// TEMP DIAGNOSTIC` blocks when done.

> ⚠️ **CAVEAT (added 2026-11-19):** the run analyzed here used the **buggy DISPERSAL time-gate** (fixed 2026-11-19; see `CHANGELOG.md`). That bug truncated the breeding season at ~day 118 instead of ~day 219, so the **TIMEGATE rate and the entire dispersal→pairing funnel below are distorted and should be re-measured on a fresh run.** The **sex-ratio / male-surplus root cause (survival-driven) is independent of this gate and still holds.**

---

## Question

In the dispersal/pairing pipeline (`LBVIAgent` `case DISPERSAL` → `NESTTERRSELECT` → `PAIR`), where are breeding adults being lost? Is a shortage of suitable territories routing adults into `NONBREEDING`?

## Method

Added temporary counters that write one row per outcome to `logDispersalOutcome.csv`
(header: `Year, Date, vireoID, Sex, AgeClass, Outcome, ListWasEmpty`):

| Outcome | Meaning |
|---|---|
| `SETTLE` | DISPERSAL found a non-empty candidate list → NESTTERRSELECT |
| `TIMEGATE` | DISPERSAL gave up → NONBREEDING because the nesting time-gate (`LBVIAgent.java` ~:126) tripped |
| `NESTTERR_PAIR` | NESTTERRSELECT chose a territory → PAIR |
| `NESTTERR_REJECT` | NESTTERRSELECT rejected all candidates → back to DISPERSAL |
| `PAIR_OK` | female found a male on her territory → NESTBUILDING |
| `PAIR_FAIL` | female settled but no male present → release territory, re-disperse |

Run analyzed: 92,305 events. Active selection trait: `mpTerrSelectionTrait = 3` (mate-availability first, then habitat quality).

## Results — the dispersal → pairing funnel

| Stage | Females | Males |
|---|---:|---:|
| Unique birds dispersing | 4,541 | 6,451 |
| SETTLE (found a list) | 13,971 | 22,795 |
| TIMEGATE (never found a list → NONBREEDING) | 1,634 | 3,168 |
| NESTTERR_PAIR (selected a territory) | 13,971 | 22,795 |
| **NESTTERR_REJECT** | **0** | **0** |
| PAIR_OK (female found a male) | 7,852 | — |
| PAIR_FAIL (female settled, no male → re-disperse) | 6,119 | — |

(Males are passive at PAIR — they wait for a female — so they log no PAIR_OK/PAIR_FAIL.)

Derived:
- **Territory discovery** succeeds for ~88% of dispersers; ~12% time out (`TIMEGATE`).
- **Territory selection is never a filter:** `NESTTERR_REJECT = 0`. Once a bird has a list, it always picks a territory.
- **Female pairing attempts:** 56.2% OK vs **43.8% FAIL**. Failing females average **4.08** failed attempts each (max 47), i.e. heavy re-disperse churn.
- **Unique females:** 90.4% (4,104/4,541) pair at least once; only 9.6% (437) never pair.
- **Sex ratio of the dispersing pool: 6,451 M : 4,541 F ≈ 1.42 : 1.** Under monogamy, ≤ 4,104 males can ever pair, so **≥ ~2,350 males (36%+) are structurally surplus and can never breed.**

## Two mechanisms behind "so many adults not breeding"

### 1. Male surplus (dominant, structural)
A 1.42:1 male:female ratio means a large block of males cannot pair regardless of behavior. They settle, park at PAIR waiting for a female, and age out into NONBREEDING. **This is the single largest contributor, and it is almost entirely a male phenomenon** (males ≥36% non-breeding vs females ~10%).

### 2. Female pairing churn (inefficiency, not a hard block)
Under trait 3, if none of a female's candidate territories currently holds a male (`mateTerrs` empty), she does **not** give up — she falls through to the `else` branch and **settles on a random territory anyway** (`LBVIAgent.java` ~:673–675), then hits PAIR, finds no male, releases, and re-disperses. This produces the 6,119 FAIL events and the churn. It wastes season-days and pushes some females past the time-gate before they can complete a nest, but 90% of females still pair eventually.

## Root cause of the male surplus — NOT seeded, it emerges

Initialization and birth are balanced:
- **Initial population is exactly 1:1** — `initAgents()` creates one male + one female per occupied territory (4,537 each). Input: `RESET_LBVI_inputData/inVireoPopulation2020.csv` (4,537 territories).
- **Birth sex ratio is 50/50** — `mpProbVireoIsFemale = 0.5`.

The surplus is therefore **emergent, driven by sex-differential survival and lifespan** (`LBVIEnvironment.java` ~:161–182). Males out-survive females at every stage, and live far longer:

| Parameter | Male | Female | M/F |
|---|---:|---:|---:|
| Juvenile survival (distribution mean) | 0.585 | 0.430 | 1.36 |
| Adult survival (distribution mean) | 0.794 | 0.595 | 1.33 |
| Max age (years) | 21 | 12 | 1.75 |

(Survival means computed from the per-run draw hyperparameters: Juv M `Gamma(156.45, 267.30)`, Juv F `Gamma(631.32, 1467.25)`, Adult M `Beta(132.94, 34.55)`, Adult F `Gamma(306.33, 514.69)`. The realized per-run draws are sampled in `start()` but not currently written to `paramList.csv`.)

Females die faster as juveniles **and** as adults, and have ~57% of the male lifespan. Compounded over years, the standing adult population becomes male-biased, which is exactly the observed ~1.42:1.

## Conclusions

1. The non-breeding adults are **not** caused by territory scarcity or selection logic — ~88% find territories and selection never rejects.
2. The dominant cause is a **~1.42:1 male:female adult sex ratio**, leaving ≥36% of males with no possible mate.
3. That sex ratio is **emergent from sex-differential survival/lifespan parameters**, not from initialization or birth ratio.
4. A secondary, smaller cost is **female pairing churn** from trait 3's "settle anyway when no male" fallback.

## Verification: is the survival asymmetry biologically intended? (2026-11-19)

**Direction — YES, intended and biologically supported.**

1. *Internal consistency (three independent places agree).* The male > female bias appears in the per-run survival draws, the stable-age startup distributions, AND the max ages — all pointing the same way with the same magnitude. A typo would show in one place; agreement across three means it was deliberately derived from a coherent demographic source (the cited "Vireo Leslie matrix").
   - Stable-age distributions decline by a near-constant per-year ratio (implied annual survival): **F ≈ 0.537, M ≈ 0.721** — gap ratio **1.34×**.
   - Survival-draw distribution means: **adult F = 0.595, M = 0.794** — gap ratio **1.33×**.
   - Max age: **M = 21, F = 12** (1.75×).
2. *Literature.* Recent USGS mark-recapture work on Least Bell's Vireo finds **sex is a top predictor of adult survival, with males higher** — San Luis Rey (2006–2023): adult males ~66% vs females ~60%; Camp Pendleton (2020–24): adult survival ~61%, sex + year the key predictors; males significantly (if slightly) higher. So a male-biased survival/sex ratio is a defensible, documented outcome for this species.

**Magnitude — the MALE side looks stronger than the field data supports.** This is the caveat worth raising with the modeler:

| Quantity | Model | Published (USGS) | Assessment |
|---|---:|---:|---|
| Adult female survival | 0.595 | ~0.60 | ✅ matches |
| Adult male survival | 0.794 | ~0.66 | ⚠️ ~0.13 too high |
| Adult M−F survival gap | ~0.20 (1.33×) | ~0.05–0.06 (~1.1×) | ⚠️ ~3× the published gap |
| Max age, male | 21 yr | far lower in banding records | ⚠️ implausibly long |

- The model's **adult female survival (0.60) matches the literature well**; the asymmetry is driven almost entirely by the **high male adult survival (0.79)** and the **21-year male lifespan**, both of which exceed what recent field estimates suggest.
- The published M−F gap is ~5–6 percentage points (ratio ~1.1×); the model's is ~20 points (ratio ~1.33×). That larger gap, compounded over years and amplified by the long male age tail, is what inflates the standing adult sex ratio to ~1.42:1 and produces the large non-breeding male surplus.
- Minor internal note: the stable-age distributions imply slightly *lower* survival (F 0.537 / M 0.721) than the survival-draw means (F 0.595 / M 0.794) — a ~0.06 offset, consistent with a Leslie matrix λ slightly > 1, not a red flag on its own.

**Verdict:** The *direction* of the asymmetry (males survive better, male-biased sex ratio) is biologically intended and literature-supported — the many non-breeding males are, qualitatively, a **correct** model behavior, not a dispersal/territory bug. However, the *size* of the male advantage (adult male survival 0.79 and max age 21) appears **larger than the published Least Bell's Vireo estimates**, so the ~1.42:1 ratio — and thus the sheer number of non-breeders — may be overstated. **Confirm the source of the male adult-survival and max-age values** (the specific Leslie matrix / PVA they were taken from, and whether they are *true* vs *apparent* survival). Note apparent survival from mark-recapture is depressed by emigration, so true survival can legitimately exceed field apparent-survival numbers — which is why the source, not just the magnitude, is what settles it.

## Recommended next steps

- **Verify the survival/lifespan asymmetry is biologically intended.** The strong pro-male bias at every stage (and 21 vs 12 max age) is the real lever: if intended, a male-biased sex ratio and many non-breeding males is a *correct* model outcome, not a bug. If not intended, revisit `mpSurvivalProb*` hyperparameters and `mpVireoPopMaxAge{M,F}`.
- **Consider snapshotting the realized per-run survival draws** into `paramList.csv` for reproducibility (currently only the hyperparameters are recorded).
- **Optional:** reduce female churn by making trait 3 *not* settle when no male is available (skip the random fallback), so she re-disperses without claiming/releasing a dead-end territory.
- **Remove the temporary `logDispersalOutcome.csv` diagnostic** once this question is closed (all `// TEMP DIAGNOSTIC` blocks in `LBVIAgent.java` and `LBVIEnvironment.java`; see `CHANGELOG.md`).

## Reproduce

```bash
cd /Users/kaiyinlin/Documents/4_QuickShare
# funnel totals
tail -n +2 logDispersalOutcome.csv | awk -F, '{print $6}' | sort | uniq -c
# by sex
tail -n +2 logDispersalOutcome.csv | awk -F, '{print $6","$4}' | sort | uniq -c
# female pairing success
tail -n +2 logDispersalOutcome.csv | awk -F, '$4=="F" && ($6=="PAIR_OK"||$6=="PAIR_FAIL"){c[$6]++} END{tot=c["PAIR_OK"]+c["PAIR_FAIL"]; printf "OK %d (%.1f%%)  FAIL %d (%.1f%%)\n", c["PAIR_OK"],100*c["PAIR_OK"]/tot, c["PAIR_FAIL"],100*c["PAIR_FAIL"]/tot}'
```
