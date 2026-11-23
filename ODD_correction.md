# ODD Corrections Log

A running log of wording/content changes needed in the RESET ODD
(*RESET ODD Version 3 PreVerification Version*). Newest entry at the top. Each entry notes the
date, the ODD section, what is wrong, and the proposed fix. The *code* is the source of truth
unless stated otherwise — most entries here bring the ODD text in line with the implemented model.

---

## 2026-11-23 — Output files: "false start" events moved from the success log to the mortality log

**Status:** the *code* changed; any ODD description of where false-start events are recorded must be brought in line.
**Section:** the output-files listing (which events belong to `logSuccessEvents.csv` vs `logMortalityEvents.csv`) and the nest-building / false-start sub-model.

### What changed in the code

A "false start" (a nest abandoned before any egg is laid) was logged to `logSuccessEvents.csv`; it is now logged to `logMortalityEvents.csv`, because the team treats a false start as a loss rather than a success. "nest completion" is unchanged and stays in `logSuccessEvents.csv`. The false-start row is written in the mortality file's 11-column layout with `CowbirdID = "NA"` (no cowbird is involved in a false start). See CHANGELOG 2026-11-23.

### Why it matters for the ODD

If the ODD lists false-start events under the success-events output (or describes them as a success-file row), that is now stale: false starts are a mortality-file event. Note that a false start is still not counted as a nesting attempt or a nest loss in the population counters (`nNests`/`nPairs` are counted only at completion); only the *logging destination* changed.

---

## 2026-11-23 — Dispersal: long-distance dispersal probability is now class-specific

**Status:** the *code* changed; any ODD description of the long-distance-dispersal parameter must be brought in line.
**Section:** the dispersal sub-model (long-distance / "beyond-kernel" dispersal) and the parameter table.

### What changed in the code

The single parameter `mpVireoLongDistanceDispersal` (probability of dispersing far beyond the kernel range; one value, 0.001) was replaced by **four** parameters, one per age/sex class:

- female juvenile `mpVireoLongDistanceDispersalFJu` = 0.001
- female adult `mpVireoLongDistanceDispersalFAd` = 0.0196
- male juvenile `mpVireoLongDistanceDispersalMJu` = 0.0491
- male adult `mpVireoLongDistanceDispersalMAd` = 0.0165

The dispersal routine already branched by class; each branch's long-distance Bernoulli draw now uses its own class parameter. The lower/upper LDD cutoffs and the per-class regular-distance kernels are unchanged. See CHANGELOG 2026-11-23.

### Why it matters for the ODD

If the ODD describes long-distance dispersal as governed by a single probability applied to all birds, that is no longer true: the probability varies by age/sex class. The parameter table should list the four parameters and their default values, and the sub-model text should state that the long-distance draw uses the disperser's own class rate (juveniles and adults, males and females, differ).

---

## 2026-11-23 — Output files: Year column added everywhere; CowbirdID on cowbird-caused egg depredation and desertion

**Status:** the *code* changed; any ODD description of the output-file columns must be brought in line.
**Section:** the output-files listing (column definitions for `logMortalityEvents.csv`, `logSuccessEvents.csv`, `logDispersalDistance.csv`, `logCowbirdArrivalOrDeparture.csv`).

### What changed in the code

1. A `Year` column (1-based simulation year) was added immediately after the step column to the four time-series logs that lacked one: `logMortalityEvents.csv`, `logSuccessEvents.csv`, `logDispersalDistance.csv`, `logCowbirdArrivalOrDeparture.csv`. (`logReproductivePerformance.csv` and `logDebug.csv` already had a year column.)
2. In `logMortalityEvents.csv`, the `CowbirdID` column is now populated for `"egg depredation-Cowbird"` and `"nest desertion due to cowbird"` events (the responsible/parasitizing Cowbird), matching how `"cowbird parasitism"` already records it. Previously these two events logged `CowbirdID = NA`.

See CHANGELOG 2026-11-23.

### Why it matters for the ODD

If the ODD lists the columns of these output files, those lists are now stale (extra `Year` column; `CowbirdID` no longer always `NA` for cowbird-caused mortality). Update the column definitions, and note that cowbird-caused egg depredation and desertion are now attributable to a CowbirdID. Caveat for the ODD text: when a nest is parasitized by multiple Cowbirds across days, the attributed CowbirdID is the most recent parasitizing Cowbird (individual eggs are not tracked).

---

## 2026-11-23 — Territory selection: males and females now follow different rules

**Status:** the *code* changed; any ODD description of nest-territory selection / pairing that treats the sexes identically must be brought in line.
**Section:** the nest-territory-selection sub-model (and any text describing how the `mpTerrSelectionTrait` strategies apply to each sex).

### What changed in the code

Territory selection is now sex-specific. A **male** establishes and defends a territory independent of female presence — he does not evaluate mate (female) availability. A **female** still selects per the `mpTerrSelectionTrait` strategy, which for her includes male availability. Previously both sexes ran the identical trait logic, so a male sought territories that already held a female. See CHANGELOG 2026-11-23.

Male rule by trait: traits 0/2/3 (quality component) → defend the best-quality territory (random among equal-top-quality ties); traits 1 (mate-only) and 4 (random) → defend a random suitable territory. The same-sex-occupancy and `terrQuality==0` exclusions are unchanged and apply to both sexes.

### Why it matters for the ODD

If the ODD states that the selection-trait strategy (habitat quality / mate availability / combinations / random) applies the same way to both sexes, that is no longer true. The sub-model should describe males as settling/defending first (mate availability not a factor for them) and females as choosing among territories, weighing male availability per the trait. Note the pending team decision (CHANGELOG 2026-11-23) on how males should behave under the mate-only and random traits — the ODD wording for those two traits should not be finalized until the team resolves it.

---

## 2026-11-22 — Scheduling: dispersal, territory selection and pairing now occur in one time-step

**Status:** the *code* changed; any ODD description of the dispersal / territory-selection / pairing schedule must be brought in line.
**Section:** the sub-models for dispersal, nest-territory selection and pairing, and anywhere the ODD states the time step on which each occurs.

### What changed in the code

The `DISPERSAL`, `NESTTERRSELECT` and `PAIR` behaviours now all execute within a **single day (one time-step)** instead of one day per stage. An agent disperses, selects a territory, and attempts to pair in the same step. A female who finds no mate returns to dispersal and retries the next day; a male who has claimed a territory waits in the pairing state across days for a female to settle. See CHANGELOG 2026-11-22.

### Why it matters for the ODD

If the ODD describes these three processes as occurring on separate/consecutive days (or assigns each its own time step), that no longer holds: a settling attempt is now a single day's activity. `ARRIVAL` remains a separate day (a bird arrives on its arrival date and settles the following day). Any schedule diagram or process-overview text should show dispersal→selection→pairing as one daily step, with across-day retries for unsettled/unpaired birds.

---

## 2026-11-22 — Initialization: year-1 input agents now arrive before nesting

**Status:** the *code* changed; any ODD description of model initialization / the first simulated year must be brought in line.
**Section:** wherever the ODD describes the initial population setup and the start of the first breeding season (initialization, and the arrival/schedule sub-model).

### What changed in the code

Previously the initial (input) pairs were placed directly in the `NESTBUILDING` state at model start, so they began breeding on day 0 of year 1 with no arrival-date constraint. They now start in `ARRIVAL` and run the same arrival-date algorithm as every other year; because they already hold their input territory and mate, on arrival they proceed directly to `NESTBUILDING`, skipping the dispersal, territory-selection and pairing stages (via a one-time `initialBreeder` flag). See CHANGELOG 2026-11-22.

### Why it matters for the ODD

If the ODD states or implies that the initial population begins nest-building at the start of year 1 (or otherwise breeds on an unconstrained year-1 schedule), that no longer holds: year 1 is now gated by the spring arrival window like every subsequent year. The initialization text should say that input birds enter in the `ARRIVAL` state, draw an arrival date, and — because their territory and mate are given by the input — bypass dispersal/territory-selection/pairing on first arrival only. This was the cause of the previously observed abnormally high year-1 reproductive output.

---

## 2026-11-21 — Output files: `logDebug.csv` log-on-change mode

**Status:** the *code* changed; any ODD/documentation description of the debug output must be brought in line.
**Section:** wherever the ODD describes model outputs / the `logDebug.csv` diagnostic file (output listing).

### What changed in the code

A new model parameter `debugLogOnChange` (Model-tab checkbox; see CHANGELOG 2026-11-21) selects how
`logDebug.csv` is written:

- **`false` — full (default):** one row per agent per active time-step, as before.
- **`true` — log-on-change (compact):** a row is written only when an agent's state-defining columns change
  (stage, location, `potentialTerrCount`, territory male/female IDs, mate ID/status, age class). Steps on
  which only the clock advanced produce no row.

Both modes still suppress the pre-arrival (`ARRIVAL`) and post-reset `NONBREEDING` idle windows, and both
emit the identical CSV header and column order.

### Why it matters for the ODD

Still a diagnostics/logging option, not a change to model dynamics. But if the ODD documents `logDebug.csv`,
it should state that the file has two modes and that, under log-on-change, each row is a **state transition**
for an agent, **not** a complete per-step record — rows are absent for steps where the agent's state was
unchanged. Population aggregates and full life-histories remain recoverable in either mode (no agent or year
is dropped); log-on-change only omits repeated-state rows.

## 2026-11-20 — Output files: `logDebug.csv` gains a `Year` column and no longer logs the non-breeding idle

**Status:** the *code* changed; any ODD/documentation description of the debug output must be brought in line.
**Section:** wherever the ODD describes model outputs / the `logDebug.csv` diagnostic file (output listing).

### What changed in the code

`logDebug.csv` is a per-agent, per-time-step diagnostic log. Two changes (see CHANGELOG 2026-11-20,
"add Year column to logDebug.csv and suppress post-reset NONBREEDING idle rows"):

1. **New `Year` column.** The header is now
   `currentStep, Year, Date, vireoID, sex, ageClass, LBVIStage, arrivalDate, currentLoc, potentialTerrCount, terrMaleID, terrFemaleID, mateID, mateStatus`.
   `Year` is the 1-based simulation year (`schedule.getSteps() / 364 + 1`); it sits between `currentStep`
   and `Date`.
2. **Reduced row set.** The file no longer contains a row for every agent on every one of the 364 daily
   time-steps. Rows are suppressed for (a) the pre-arrival window (stage `ARRIVAL`) and (b) the
   post-reset `NONBREEDING` idle. Each agent therefore contributes **exactly one `NONBREEDING` row per
   year** — the step on which its end-of-season winter reset executes — plus its active-stage rows.

### Why it matters for the ODD

This is a diagnostics/logging change, not a change to model dynamics (no process or state variable is
altered). But if the ODD documents the output files — their columns or the meaning of "one row per
agent per time-step" — that description is now stale: the column list must include `Year`, and the file
must be described as event/active-stage rows plus a single annual non-breeding reset row, **not** a
complete daily record. The reset's substantive effects (interannual mortality, aging, `nSingles`)
remain fully recorded in `logReproductivePerformance.csv` and `logMortalityEvents.csv`, so no
information is lost by the reduction.

## 2026-11-20 — §3.20: Over-winter juvenile survivors age to adult (age 1), not reset to age 0 / "young"

**Status:** the *code* is correct; the *document* is stale.
**Section:** 3.20 "Inter-annual Vireo Mortality during the Non-breeding Season"

### The problem

ODD §3.20 lists the state-variable changes a **surviving young-of-the-year** makes during
`actInterannualMortality`. Two bullets contradict the implemented (and ecologically correct)
behavior:

> - Their `(vireoAgeYears)` value is set to **0**.
> - Their `(vireoAgeClass)` value is set to **"young"**.

Taken literally, a juvenile that survives its first winter would re-enter the next breeding season
still at age 0 and still classed "young." It would therefore never become a breeding adult, and the
population could never recruit new breeders — inconsistent with the rest of the model (birds breed
at age 1).

### What the code actually does

A natal-year juvenile sits out its birth season in `NONBREEDING` (the "Bug C" fix), and the
end-of-season reset (`LBVIAgent.updateLBVIStateVariables()`) **ages the survivor by one year** and
reclassifies it as an adult:

```java
this.vireoAgeYears++;                 // 0 -> 1
if (this.vireoAgeYears >= 1) {        // now an adult
    this.vireoAgeClassAdult = true;
}
```

So a bird born in year *t* returns in year *t+1* as a **1-year-old adult** and breeds that season
(see CHANGELOG 2026-11-18, "Bug C — newborns bred in their natal year").

### Proposed replacement wording for §3.20

Replace the two bullets above (in the "young of the year … survive" list) with:

> - Their `(vireoAgeYears)` value is **incremented by one** (from 0 to 1).
> - Their `(vireoAgeClass)` value is set to **"adult"**, because a bird that survives its first
>   non-breeding season returns to the breeding range the following year as a one-year-old breeder.
>   (Newborns spend their natal season in a non-breeding state and do not breed in the year they are
>   hatched.)

The remaining bullets in that list are unchanged (parent recruit count, starting location set to the
natal territory, sex assignment, arrival-date draw, and the mate-status / reproductive-stage reset).

### Related note (not part of this wording change)

§3.20 also states the surviving juvenile's ID is "added to their female parent's `(vireoNumRecruits)`"
— i.e. the ODD counts **recruits as the offspring that survive their first winter**. The current
implementation increments `vireoNumRecruits` **at birth** (counting all offspring produced that
season, i.e. fecundity), matching the field's in-code definition ("number of babies in current
breeding season"). If the team wants the strict §3.20 meaning (survived-to-recruit), the increment
must move to the juvenile-survival branch of `interannualMortality()` and each offspring must carry a
reference to its parent. Flagged so the two definitions are not conflated when this section is revised.
