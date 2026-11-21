# ODD Corrections Log

A running log of wording/content changes needed in the RESET ODD
(*RESET ODD Version 3 PreVerification Version*). Newest entry at the top. Each entry notes the
date, the ODD section, what is wrong, and the proposed fix. The *code* is the source of truth
unless stated otherwise — most entries here bring the ODD text in line with the implemented model.

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
