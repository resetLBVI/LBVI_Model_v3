package lbvi;

import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.Observer;

/*
When there are no Vireos remaining in the breeding range, the actBreedingSeasonSummary action outputs the following
statistics to the ‘logReproductionSummary.csv' output file that sum outcomes across all Vireos for the whole year t
breeding season (see RESET User Guide for more details on the 'logReproductionSummary.csv’ output):
•	|NPairs|- the number of unique female Vireos that created least one Nest.
•	|NSingles|- the number of unique Vireos that never had a Mate.
•	|NNests| - the total number of unique nests created.
•	|NEggs| - the total number of eggs laid.
•	|NNestlings| - the total number of eggs that hatched.
•	|NFledgling| - the total number of nestlings that survived to fledging.
•	|NFledIndep| - the total number of fledglings that survived to independence.
•	|NSuccNests| - the total number of nests that produced at least one fledgling.
•	|NCbrdArriv|- the total number of cowbirds that arrived in the model
•	|NParasit| - the total number of cowbird parasitism events.
•	|NCowbrdCap| - the total number of cowbirds captured in cowbird traps prior to parasitizing a Vireo Nest.

 */

public class LBVIObserver implements Steppable {

    @Override
    public void step(SimState simState) {
        LBVIEnvironment eState = (LBVIEnvironment) simState;
        //collect population data once per year at the end of the breeding season
        if (eState.schedule.getSteps() > 0 && eState.schedule.getSteps() % 364 == 0) {
            collectPopData(eState);
            reset(eState);
        }
        //stop the simulation once it has run the configured number of years (1 year = 364 steps). This runs
        //after the year-boundary collection above, so the final year's summary is still logged before we stop.
        if (eState.schedule.getSteps() >= (long) eState.mpInputSimulationDurationYears * 364L) {
            simState.kill();
        }
    }

    /*
    *************************************************************************************
    *                                     Data Collection
    * ***********************************************************************************
     */
    private void collectPopData(LBVIEnvironment eState) {
        //start writing
        eState.populationSize = eState.vegetationGrid.getAllObjects().numObjs;
        // Collection runs at a year boundary (first step of the next year), so currentYear is already the
        // NEW year; label this summary with the year that just completed (currentYear - 1).
        // Observer labels the completed year (currentYear - 1), since collection fires on the first step of the next year when currentYear has already ticked over. With
        //  the 30-year default you'll now get clean rows for years 1–30.
        String popInfo = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", eState.currentYear - 1,
                eState.populationSize, eState.nPairs, eState.nSingles, eState.nNests, eState.nEggs, eState.nNestlings,
                eState.nFledglings, eState.nFledIndipendence, eState.nSuccNests, eState.nCowbirdArrival,
                eState.nParasitism, eState.nCowbirdCaptured); //13 attributes
        eState.logReproductivePerformanceWriter.addToFile(popInfo);

    }

    private void reset(LBVIEnvironment state) {
        state.nPairs = 0; //|NPairs|- the number of unique female Vireos that created least one Nest.
        state.nSingles = 0; //|NSingles|- the number of unique Vireos that never had a Mate.
        state.nNests = 0; //|NNests| - the total number of unique nests created.
        state.nEggs = 0; //|NEggs| - the total number of eggs laid.
        state.nNestlings = 0; //|NNestlings| - the total number of eggs that hatched.
        state.nFledglings = 0; //|NFledgling| - the total number of nestlings that survived to fledging.
        state.nFledIndipendence = 0; //|NFledIndep| - the total number of fledglings that survived to independence.
        state.nSuccNests = 0; //|NSuccNests| - the total number of nests that produced at least one fledgling.
        state.nCowbirdArrival = 0; //|NCbrdArriv|- the total number of cowbirds that arrived in the model
        state.nParasitism = 0; //|NParasit| - the total number of cowbird parasitism events.
        state.nCowbirdCaptured = 0;
    }
}
