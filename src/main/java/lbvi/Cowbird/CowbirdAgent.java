package lbvi.Cowbird;

import lbvi.LBVIEnvironment;
import lbvi.Nest;
import lbvi.Traps.TrapInfoIndentifier;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;

import java.util.ArrayList;
import java.util.List;

public class CowbirdAgent implements Steppable {
    public int cowbirdID;
    public int cowbirdCurrentTerrID; //the terrID of the cowbird's current Location
    public int cowbirdNDaysLeft;
    public double cowbirdCoordX;
    public double cowbirdCoordY;

    public Stoppable event; //allow the removal of an agent from the shcedule

    public CowbirdAgent(LBVIEnvironment state, int cowbirdID, int cowbirdCurrentTerrID, int cowbirdNDaysLeft) {
        this.cowbirdID = cowbirdID;
        this.cowbirdCurrentTerrID = cowbirdCurrentTerrID;
        this.cowbirdNDaysLeft = cowbirdNDaysLeft;
        this.cowbirdCoordX = state.vegTerrInfo.get(cowbirdCurrentTerrID).terrCoordX;
        this.cowbirdCoordY = state.vegTerrInfo.get(cowbirdCurrentTerrID).terrCoordY;
    }

    /**
     * ODD 3.15 - arrival. Rolls the date-adjusted arrival probability for the current day; if a Cowbird
     * arrives it is created with a fresh cowbirdID and a full days-left budget (mpNCowbirdEggsPerSeason),
     * scheduled, logged, and immediately resolved for its arrival day (capture check, else lay one egg).
     * Returns the new agent, or null if no Cowbird arrives. Called by an eligible Nest (ODD 3.15).
     */
    public static CowbirdAgent tryArrival(LBVIEnvironment state, Nest nest) {
        if (!(state.random.nextDouble() < arrivalProb(state, state.currentJulianDay))) {
            return null;
        }
        state.nCowbirdArrival++;
        CowbirdAgent cowbird = new CowbirdAgent(state, state.cowbirdID++, nest.nestTerrID, state.mpNCowbirdEggsPerSeason);
        cowbird.event = state.schedule.scheduleRepeating(cowbird);
        cowbird.logMovement(state, "Arrival");
        cowbird.resolveArrivalDay(state, nest);
        return cowbird;
    }

    /**
     * ODD 3.15 - triangular date-adjusted arrival probability. Rises linearly from Min to Max between
     * mpCowbirdFirstDate and mpPeakCowbirdDate, then falls back to Min by mpCowbirdLastDate; Min outside
     * that window. (Moved here from Nest: it is a property of the Cowbird population, not of any nest.)
     */
    private static double arrivalProb(LBVIEnvironment state, int julianDay) {
        double minProb = state.mpMinCowbirdArrivalProb;
        double maxProb = state.mpMaxCowbirdArrivalProb;
        int firstDate  = state.mpCowbirdFirstDate;
        int peakDate   = state.mpPeakCowbirdDate;
        int lastDate   = state.mpCowbirdLastDate;
        if (julianDay < firstDate || julianDay > lastDate) {
            return minProb;
        } else if (julianDay <= peakDate) {
            return minProb + (maxProb - minProb) * (double)(julianDay - firstDate) / (peakDate - firstDate);
        } else {
            return minProb + (maxProb - minProb) * (double)(lastDate - julianDay) / (lastDate - peakDate);
        }
    }

    /**
     * ODD 3.15 tail + 3.16 for the arrival day: at the nest that triggered arrival, first resolve capture
     * (3.17); if not captured, lay one egg (3.16). The arrival day counts against the days-left budget.
     */
    private void resolveArrivalDay(LBVIEnvironment state, Nest nest) {
        if (tryCapture(state)) {
            return;
        }
        parasitize(state, nest);
        spendDayAndMaybeDepart(state);
    }

    @Override
    public void step(SimState simState) {
        LBVIEnvironment state = (LBVIEnvironment) simState;
        // ODD 3.19: at the start of each day the Cowbird re-runs the capture check before acting.
        if (tryCapture(state)) {
            return;
        }
        cowbirdNestSearch(state, state.activeNests);
        spendDayAndMaybeDepart(state);
    }

    /**
     * Capture check (ODD 3.17/3.19). isCaptured() only draws against traps within mpMaxCowbirdCaptureDistM,
     * so it is a no-op when no trap is in range. On capture the Cowbird is logged, removed, and true returned.
     */
    private boolean tryCapture(LBVIEnvironment state) {
        List<TrapInfoIndentifier> openTraps = logisticFunForCapture.findOpenTraps(state);
        if (logisticFunForCapture.isCaptured(state, this.cowbirdCurrentTerrID, openTraps)) {
            state.nCowbirdCaptured++;
            this.cowbirdCaptured(state);
            return true;
        }
        return false;
    }

    /**
     * FLAG A - cowbirdNDaysLeft is a budget of DAYS: one day is spent per calendar day the Cowbird is in
     * the model (whether or not it laid an egg), and it departs the day the budget reaches zero.
     */
    private void spendDayAndMaybeDepart(LBVIEnvironment state) {
        this.cowbirdNDaysLeft--;
        if (this.cowbirdNDaysLeft <= 0) {
            this.cowbirdDeath(state);
        }
    }

    /** ODD 3.16: lay one Cowbird egg in the given (eligible Vireo) nest. */
    private void parasitize(LBVIEnvironment state, Nest nest) {
        nest.cowbirdEggLayingInVireoNests(state);
    }

    /**
     * The cowbird searches for a nest to parasitize on the current day.
     *
     * (1) Random draw vs mpNextNestIsVireo:
     *     - draw > mpNextNestIsVireo → parasitize a different host species; decrement cowbirdNDaysLeft and return.
     *     - draw < mpNextNestIsVireo → search for an eligible Vireo nest.
     * (2) Build a square neighborhood of side mpCowbirdNestSearchDistance centred on the cowbird's
     *     current coordinates (half-width = mpCowbirdNestSearchDistance / 2).
     * (3) Collect all Vireo nests within the square that are eligible for parasitism (ODD 3.17).
     * (4) If no eligible nests are found, decrement cowbirdNDaysLeft and return.
     * (5) Randomly select one eligible nest, move the cowbird to that territory, and call
     *     cowbirdParasitism() on it.
     *
     * @param state     the simulation environment
     * @param allNests  all currently active Vireo nests (state.activeNests)
     */
    public void cowbirdNestSearch(LBVIEnvironment state, List<Nest> allNests) {
        // (1) some days the Cowbird targets a non-modelled host species instead of a Vireo nest. The day
        //     is still spent (by the caller); it simply lays nothing here.
        if (!state.random.nextBoolean(state.mpCowbirdNextNestIsVireo)) {
            return;
        }
        // (2) square neighbourhood: side = mpCowbirdNestSearchDistance, centred on cowbird
        double halfSide = state.mpCowbirdNestSearchDistanceM / 2.0;
        double xMin = this.cowbirdCoordX - halfSide;
        double xMax = this.cowbirdCoordX + halfSide;
        double yMin = this.cowbirdCoordY - halfSide;
        double yMax = this.cowbirdCoordY + halfSide;
        // (3) collect eligible Vireo nests (>=1 Vireo egg) within the square
        List<Nest> eligibleNests = new ArrayList<>();
        for (Nest nest : allNests) {
            if (nest.nestCoordX >= xMin && nest.nestCoordX <= xMax &&
                nest.nestCoordY >= yMin && nest.nestCoordY <= yMax &&
                nest.isEligibleForParasitism(state)) {
                eligibleNests.add(nest);
            }
        }
        // (4) no eligible nest today: lay nothing (the day is still spent by the caller)
        if (eligibleNests.isEmpty()) {
            return;
        }
        // (5) randomly select a nest, move to it, and parasitize
        Nest targetNest = eligibleNests.get(state.random.nextInt(eligibleNests.size()));
        this.cowbirdCurrentTerrID = targetNest.nestTerrID;
        this.cowbirdCoordX = targetNest.nestCoordX;
        this.cowbirdCoordY = targetNest.nestCoordY;
        parasitize(state, targetNest);
    }

    public void cowbirdCaptured(LBVIEnvironment state) { //captured at a trap and removed from the simulation
        logMovement(state, "Captured");
        event.stop();
    }

    public void cowbirdDeath(LBVIEnvironment state) { //leaving the simulation for the season
        logMovement(state, "Departure");
        event.stop();
    }

    // logCowbirdArrivalOrDeparture.csv: "currentStep", "date", "terrID", "cowbirdID", "ArrivalOrDeparture"
    private void logMovement(LBVIEnvironment state, String arrivalOrDeparture) {
        String record = String.format("%s,%s,%s,%s,%s", state.schedule.getSteps(), state.currentJulianDay,
                this.cowbirdCurrentTerrID, this.cowbirdID, arrivalOrDeparture);
        state.logCowbirdArrivalOrDepartureWriter.addToFile(record);
    }

}
