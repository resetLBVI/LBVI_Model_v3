# Changelog

A running log of code changes and bug fixes to the RESET LBVI model, so the team can track what changed, when, and why — and make sure nothing gets overlooked.

**How to add an entry:** put the newest change at the top. Use the date (YYYY-MM-DD), tag it `Change`, `Bug fix`, `Feature`, or `Refactor`, and note the files touched and the reason. Keep it short but specific enough to reproduce or reverse.

---

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
