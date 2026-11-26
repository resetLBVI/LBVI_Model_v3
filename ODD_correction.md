# ODD Corrections Log

A running log of wording/content changes needed in the RESET ODD
(*RESET ODD Version 3 PreVerification Version*). Newest entry at the top. Each entry notes the
date, the ODD section, what is wrong, and the proposed fix. The *code* is the source of truth
unless stated otherwise — most entries here bring the ODD text in line with the implemented model.

---

## 2026-11-24 — PSHB beetle impacts (ODD 3.1 actBeetleImpact, 3.22 recovery, 6.4 input)

**Status:** code implements the beetle feature; several ODD wording/content points need the team's attention. The *code* resolves each as noted; ODD text should be brought into line (or the team should correct the code if a choice was wrong).

**Sections:** 3.1 (Beetle Impacts on Riparian Vegetation), 3.22 (Vegetation Recovery from Beetle Damage), 6.4 (Annual Beetle Impacts Input), state-variable glossary.

1. **"very stressed" has no matching VegHealth label.** 3.1 sets `territoryVegHealth` to "very stressed", but the code's `VegHealth` enum is 5-valued: Very Healthy(4), Healthy(3), Stressed(2), Severely Stressed(1), Dead(0) — there is no "very stressed". The new ODD's 4-value scale ("very healthy, healthy, stressed, very stressed") also drops "dead". **Code maps "very stressed" → SEVERELYSTRESSED (code 1)** (lowest non-dead tier; recovery later goes to Healthy). ODD and enum vocabularies should be reconciled (rename enum, or fix ODD to "severely stressed", and decide whether "dead" still exists).

2. **3.22 restore-direction looks backwards.** 3.22 says on recovery to "set the value of their (territoryPreBeetleDamageQuality) variable to be the current value of (territoryQuality)" — but the glossary (territoryPreBeetleImpactQuality) says that variable exists to **restore** `territoryQuality` after recovery. Taken literally the 3.22 assignment would copy 0 into the pre-damage field and never restore quality. **Code follows the glossary's stated intent: `terrQuality = terrPreBeetleDamageQuality` on recovery.** ODD 3.22 wording should be flipped to say quality is restored from the pre-damage value.

3. **3.1 cross-reference points to the wrong section.** 3.1 ends "For riparian vegetation regrowth and recovery after PSHB damage, see 3.18." Recovery is actually **3.22**; 3.18 is "End Vireo Breeding Season". Fix the cross-reference to 3.22.

4. **Variable name inconsistency.** The glossary calls it `territoryPreBeetleImpactQuality`; 3.1/3.22 call it `territoryPreBeetleDamageQuality` (which matches the code field `terrPreBeetleDamageQuality`). Pick one name throughout the ODD.

5. **Input filename & that it is a 100-replicate file.** 3.1 names `inBeetleImpact.csv`; 6.4 names `inBeetleImpacts.csv`. The real inputs are per-scenario — `inBeetleImpact_Pendleton.csv`, `inBeetleImpact_SantaClaraCentral.csv`, `inBeetleImpact_SantaClaraEast.csv` — and each holds **100 stochastic PSHB replicates** (`runNum` 1–100) with columns `runNum, hit.year, hit.terr`. 6.4's "a single record each year for each territory" understates this. ODD should: name the scenario files, describe the three columns, state that one `runNum` is applied per LBVI run (model param `mpBeetleRunNum`), and that `hit.year` (the PSHB timeline, 1985-based) is offset-mapped onto the LBVI simulation year via `mpBeetleStartYear` (default 1985 → sim year 1). None of runNum/year-mapping is currently described in the ODD.

## 2026-11-23 — End of season: max-nesting-attempts trigger now wired (ODD actEndBreedingSeason trigger b)

**Status:** the *code* was brought INTO LINE with the ODD; one naming point to confirm with the team.
**Section:** actEndBreedingSeason (the two end-of-season triggers) and the state-variable glossary (attempt counters).

### What changed in the code

The ODD ends a Vireo's breeding season when `vireoNumNestingAttempts == mpVireoMaxNumAttempts`, in addition to the date trigger. This was previously not enforced as a transition; it is now checked in RENEST (a female whose completed-nesting-attempt count reaches `mpMaxNumAttempts` goes to NONBREEDING), and the NONBREEDING reset guard uses the same counter. See CHANGELOG 2026-11-23.

### Point to confirm with the team (Casey)

The code has two attempt counters: `vireoNumNestAttempts` (incremented at clutch completion — every attempt reaching incubation, success or failure; false starts excluded) and `vireoCurrentNumAttempts` (incremented only at fledgling independence — successful attempts). The wiring uses `vireoNumNestAttempts`, matching the ODD variable name `vireoNumNestingAttempts` and the usual meaning of "nesting attempts." If the ODD intends successful attempts only, switch both sites to `vireoCurrentNumAttempts`. The ODD glossary should state explicitly which definition `vireoNumNestingAttempts` carries (recommend: attempts that reached a completed clutch, excluding false starts, per the ODD's own note that false starts do not count).

### Why it matters for the ODD

Trigger (b) now actually ends seasons, so model behavior matches the ODD text. The only open item is the precise definition of "nesting attempts," which the ODD glossary should pin down.

---

## 2026-11-23 — Renesting (actEvaluateRenesting): boundary operator, countdown timing, false-start routing aligned to ODD; one mismatch remains

**Status:** the *code* was brought INTO LINE with the ODD on three points; one mismatch remains and needs a team decision.
**Section:** actEvaluateRenesting (renesting sub-model).

### What changed in the code

1. End-of-breeding test changed from `today >= mpVireoLastPossibleNestingDate` to `today > ...` — a nest may still be started ON the last possible nesting date, per the ODD ("greater than" ends breeding; "less than or equal to" renests).
2. The renesting-interval countdown now starts nest building on the day it reaches 0 (was the day after), so the wait equals `mpVireoRenestingIntervalDuration` exactly (was interval + 1).
3. A false start now triggers renest evaluation (RENEST) as the ODD specifies (condition 3), instead of an immediate same-cycle rebuild in NESTBUILDING; it still does not count as a breeding attempt.

See CHANGELOG 2026-11-23.

### Per-day date re-check — DECIDED 2026-11-23: keep per-day (update the ODD text to match)

The ODD checks the date **once**, at the moment actEvaluateRenesting is triggered: if `today <= mpVireoLastPossibleNestingDate` the female commits to the renest and completes it after the interval, even if the rebuild day lands past the last possible nesting date. The code instead re-checks the date on **every** countdown day (the RENEST stage runs each day), so a renest countdown that begins on/before the last possible nesting date but would finish after it is cut short (the female ends breeding). This only affects triggers within about one renesting-interval of the boundary.

**Team decision (2026-11-23): keep the per-day re-check** — the stricter behavior, so that no nest ever *starts* after `mpVireoLastPossibleNestingDate`. No code change (the code already does this). **ODD action:** update the actEvaluateRenesting text to say the female re-evaluates today's date against `mpVireoLastPossibleNestingDate` on each countdown day (not only once at the triggering event), and ends the season if the date passes the cutoff before the renest interval elapses.

### Why it matters for the ODD

Points 1–3 remove code/ODD mismatches, so no ODD wording change is needed there. The remaining item is a genuine modelling choice; the ODD and code should be reconciled once the team picks (a) or (b).

---

## 2026-11-23 — End of breeding season: the female now ends her mate's season (was a male date gate)

**Status:** the *code* was brought INTO LINE with the ODD; this entry records the alignment.
**Section:** actEndBreedingSeason (end-of-breeding-season sub-model) and the male scheduling description.

### What changed in the code

The ODD states that a Vireo completing actEndBreedingSeason "schedules herself, and her mate, to execute [the end-of-season actions]… and releases the male from her control." The code now implements this: when a female runs her end-of-season (NONBREEDING) reset, she sends her mate into his own end-of-season reset (`endMateBreedingSeason`). The paired male's former self-terminating date gate was removed. An **unpaired** male (parked in PAIR, no female ever settled) still ends himself at `mpVireoLastPossibleNestingDate`, consistent with the ODD's pairing-deadline rule. See CHANGELOG 2026-11-23.

### Why it matters for the ODD

This removes a prior code/ODD mismatch (the male independently self-terminating on a date rather than being released by his mate), so no ODD wording change is needed for the actEndBreedingSeason mate-release step — the code now matches it. If the ODD's process-overview or scheduling text describes the male ending his own season on a date, align it to say the female ends her mate's season (the male runs his end-of-season actions the next day). Note: a bird with an active nest or in fledging care still continues past `mpVireoLastPossibleNestingDate` and ends when its nesting concludes — unchanged and consistent with the ODD exemption.

---

## 2026-11-23 — Arrival date: drawn directly from the triangular distribution (Poisson step removed)

**Status:** the *code* changed; any ODD description of how the arrival date is drawn must be brought in line.
**Section:** the arrival sub-model (arrival-date distribution) and any equations/parameters describing it.

### What changed in the code

`ArrivalDate.arrivalDate(lower, mode, upper)` previously sampled a value from a triangular distribution and used it as the mean (lambda) of a Poisson distribution, returning the Poisson draw. It now returns the triangular draw itself, rounded to the nearest day. The triangular parameters are unchanged (lower = `mpVireoFirstDate`/`lowerArrivalDate`, mode = `mpVireoPeakDate`/`modeArrivalDate`, upper = `mpVireoLastDate`/`upperArrivalDate`). See CHANGELOG 2026-11-23.

### Why it matters for the ODD

If the ODD states that the arrival date is a Poisson draw whose mean is a triangular sample (a two-step process), that no longer holds: the arrival date is now a single triangular draw. The expected arrival date is unchanged (triangular mean `(a+c+b)/3`), but the date is now guaranteed to lie within `[lower, upper]` and has less dispersion than the old Poisson-mixed version. Update the arrival-date equation/description accordingly and drop any mention of the Poisson step.

---

## 2026-11-23 — Dispersal: a natal-born bird's first dispersal is a juvenile (natal) dispersal

**Status:** the *code* changed; any ODD description of which dispersal kernel each bird uses must be brought in line.
**Section:** the dispersal sub-model (age/sex-specific dispersal kernels) and any text on natal vs breeding dispersal.

### What changed in the code

Because a simulation-born bird sits out its natal year in a non-breeding state and the winter reset ages it into the **adult** age class before its first breeding season, every home-grown bird was dispersing with the **adult** kernel — the juvenile kernels were never used. The code now treats a simulation-born bird's **first realized dispersal** as a juvenile (natal) dispersal: it uses the juvenile dispersal kernel and is recorded with `AgeClass = Juvenile` in `logDispersalDistance.csv`, even though the bird is in the adult age class (its age class, survival, aging and breeding are unchanged; `AgeYears` still shows the true age, typically 1). Only the first dispersal is juvenile — any later dispersal (a re-dispersal after a failed pairing, or a dispersal in a later year) uses the adult kernel. Startup (input) birds are unaffected. See CHANGELOG 2026-11-23.

### Why it matters for the ODD

If the ODD maps dispersal kernels strictly to the age *class* (adult vs juvenile), that no longer matches the implementation: the first dispersal of a natal bird uses the **juvenile** kernel despite the bird being classed adult. The sub-model should state that natal (first) dispersal uses the juvenile kernel and subsequent (breeding) dispersal uses the adult kernel, and that this natal-dispersal distinction is a dispersal-categorisation only — it does not change the bird's age class. This is also the first time the juvenile dispersal kernels are actually exercised, which the ODD's dispersal-parameterisation text should acknowledge.

---

## 2026-11-23 — Input data: parameters can now be loaded from a CSV (optional), and the model has a headless runner

**Status:** the *code* gained a feature; update the ODD only if it documents how parameters are supplied / how the model is run.
**Section:** "Input data" / "Initialization" (how model parameters are set), and any run/implementation notes.

### What changed in the code

Model parameters can optionally be loaded from a `parameter,value` CSV (the same format the reproducibility snapshot `paramList.csv` is written in) via a new `paramFilePath` field and `loadParameterList()`, applied at the top of `start()`. A new headless entry point (`LBVIEnvironment.main`) runs the model without the GUI, taking the parameter file and an optional seed as command-line arguments. When `paramFilePath` is blank, behaviour is identical to before (defaults / Model-tab values). See CHANGELOG 2026-11-23.

### Why it matters for the ODD

No model dynamics change. But if the ODD states that parameters are entered through the GUI Model tab (or only via code defaults), it can note that a configuration may instead be supplied as a CSV and that the model can be run headless (batch) from the command line. This does **not** add a parameter-sweep capability — it is single-configuration loading; a sweep would be a separate harness built on top.

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
