package lbvi.Beetles;

import lbvi.LBVIEnvironment;
import lbvi.VegHealth;
import lbvi.VegInfoIdentifier;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.List;

/**
 * Annual PSHB (Polyphagous Shot-hole Borer) beetle impact on riparian vegetation.
 *
 * Damage - ODD 3.1 (actBeetleImpact): on the FIRST day of each breeding year (getSteps() % 364 == 0, i.e.
 * before that year's spring arrival/breeding), every Territory listed for the current LBVI simulation year in
 * the beetle-impact file (loaded into {@code eState.beetleImpactByYear}, keyed by 1-based simulation year after
 * the runNum filter and the mpBeetleStartYear offset mapping are applied in loadBeetleImpact) is damaged:
 *   - terrVegHealth            -> "very stressed" (VegHealth code 1 = SEVERELYSTRESSED, the enum's lowest
 *                                 non-dead tier; the ODD's 4-value scale has no separate "severely stressed")
 *   - terrPreBeetleDamageQuality <- current terrQuality (saved so recovery can restore it)
 *   - terrQuality              -> 0 (a quality-0 territory is excluded from settlement in
 *                                 LBVIAgent.findPotentialTerritoryList, so the territory is unavailable)
 *   - terrBeetleDamage         <- mpBeetleDamageCounter (recovery countdown, measured in breeding seasons)
 *
 * Recovery - ODD 3.22 (Vegetation Recovery from Beetle Damage): at each season end, called from
 * {@link lbvi.LBVIObserver} AFTER logReproductivePerformance.csv has been written. Every Territory still in
 * the countdown (terrBeetleDamage >= 1) decrements by one; any that reach 0 recover: terrVegHealth -> "healthy"
 * (code 3) and terrQuality is RESTORED from terrPreBeetleDamageQuality, making the territory usable again.
 *
 * Scheduling: this Steppable is scheduled repeating at ordering 0 (after the clock at -1, so currentYear is
 * fresh; before the agents at +1). It shares ordering 0 with the observer, but the two never act on the same
 * day (damage on day 0, observer/recovery on day 363), so there is no ordering interaction.
 */
public class ActBeetleImpact implements Steppable {

    // ODD terminology -> VegHealth codes. "very stressed" has no exact enum label; it maps to the lowest
    // non-dead tier, SEVERELYSTRESSED (1). "healthy" maps to HEALTHY (3). See ODD_correction for the label
    // mismatch between the new ODD's 4-value health scale and the code's 5-value VegHealth enum.
    private static final int VERY_STRESSED = VegHealth.SEVERELYSTRESSED.getCode(); // 1
    private static final int HEALTHY       = VegHealth.HEALTHY.getCode();          // 3

    @Override
    public void step(SimState simState) {
        LBVIEnvironment eState = (LBVIEnvironment) simState;
        if (eState.schedule.getSteps() % 364 == 0) { // day 0 of each 364-day breeding year
            applyAnnualDamage(eState);
        }
    }

    /** ODD 3.1 - apply PSHB damage to the territories listed for the current simulation year. */
    public static void applyAnnualDamage(LBVIEnvironment eState) {
        int simYear = eState.currentYear; // 1-based; beetleImpactByYear is keyed by simulation year
        List<Integer> damaged = eState.beetleImpactByYear.get(simYear);
        if (damaged == null || damaged.isEmpty()) return; // no PSHB damage recorded for this year
        int n = 0;
        for (int terrID : damaged) {
            VegInfoIdentifier terr = eState.vegTerrInfo.get(terrID);
            if (terr == null) {
                System.err.println("actBeetleImpact: terrID " + terrID + " (sim year " + simYear
                        + ") not in vegetation data, skipping");
                continue;
            }
            // Only capture the pre-damage quality on the FIRST hit. If a territory is listed again while it
            // is still mid-countdown its current terrQuality is already 0, so re-copying would overwrite the
            // real saved value with 0 and defeat recovery; a repeat hit therefore just refreshes the counter.
            // (ODD 3.1 does not specify behaviour when a territory is damaged in consecutive years - see
            // ODD_correction.)
            if (terr.terrBeetleDamage == 0) {
                terr.terrPreBeetleDamageQuality = terr.terrQuality;
            }
            terr.terrVegHealth = VERY_STRESSED;
            terr.terrQuality = 0;
            terr.terrBeetleDamage = eState.mpBeetleDamageCounter;
            eState.logBeetleState(terr); // TEMP diagnostic: row at the moment of damage (no-op unless enabled)
            n++;
        }
        System.out.println("actBeetleImpact: sim year " + simYear + " (calendar " + eState.currentCalendarYear()
                + ") - " + n + " territory(ies) damaged by PSHB.");
    }

    /**
     * ODD 3.22 - season-end recovery. Decrement every territory still in the countdown; restore any that
     * reach 0. Processing only territories with terrBeetleDamage >= 1 (rather than "all territories at 0")
     * is deliberate: never-damaged territories sit at the default 0 and must NOT be touched, otherwise their
     * quality would be overwritten with a (zero) pre-damage value every year.
     */
    public static void recoverFromBeetleDamage(LBVIEnvironment eState) {
        int recovered = 0;
        for (VegInfoIdentifier terr : eState.vegTerrInfo.values()) {
            if (terr.terrBeetleDamage >= 1) {
                terr.terrBeetleDamage -= 1;
                if (terr.terrBeetleDamage == 0) {
                    terr.terrVegHealth = HEALTHY;
                    // ODD 3.22 as written assigns preBeetleDamageQuality <- terrQuality, but the variable's
                    // defined purpose (ODD glossary, territoryPreBeetleImpactQuality) is to RESTORE
                    // terrQuality after recovery. We restore terrQuality from the saved pre-damage value so
                    // the territory becomes usable again (quality > 0). See ODD_correction.
                    terr.terrQuality = terr.terrPreBeetleDamageQuality;
                    recovered++;
                }
                // TEMP diagnostic: one row per season-end while damaged, incl. the recovery row (no-op unless enabled)
                eState.logBeetleState(terr);
            }
        }
        if (recovered > 0) {
            System.out.println("Beetle recovery: sim year " + eState.currentYear + " - "
                    + recovered + " territory(ies) recovered to healthy.");
        }
    }
}
