package lbvi;

import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.Observer;

/**
 * Population observer. Scheduled at ordering 0 (before all agents, which run at ordering 1, and after the
 * clock at ordering -1), repeating every step. Once per year, on the LAST day of the 364-day breeding year
 * (getSteps() % 364 == 363), it appends one summary row to logReproductivePerformance.csv and then zeros the
 * model-level counters for the next year. Day 363 is chosen because every agent has finished its end-of-season
 * winter reset by then (those resets, around day ~219-220, are what increment nSingles), so the counts are
 * complete, yet the row is still written within the year it describes — so it is labelled with the current year
 * directly, no off-by-one. Each row has 13 columns: Year, PopulationSize, then the eleven statistics below,
 * which sum outcomes across all Vireos for that year's breeding season (see RESET User Guide for more detail):
 *   - NPairs    - the number of unique female Vireos that created at least one Nest.
 *   - NSingles  - the number of unique breeding-eligible adult Vireos that never had a Mate this year.
 *   - NNests    - the total number of unique (completed) nests created.
 *   - NEggs     - the total number of eggs laid.
 *   - NNestlings- the total number of eggs that hatched.
 *   - NFledgling- the total number of nestlings that survived to fledging.
 *   - NFledIndep- the total number of fledglings that survived to independence.
 *   - NSuccNests- the total number of nests that produced at least one fledgling.
 *   - NCbrdArriv- the total number of cowbirds that arrived in the model.
 *   - NParasit  - the total number of cowbird parasitism events.
 *   - NCowbrdCap- the total number of cowbirds captured in cowbird traps prior to parasitizing a Vireo Nest.
 *
 * A year-0 row capturing the initial (input) population is written separately in LBVIEnvironment.start().
 * The run is stopped here once getSteps() reaches mpInputSimulationDurationYears * 364 - 1, in the same step
 * that collects the final year, so every configured year is logged exactly once.
 */

public class LBVIObserver implements Steppable {

    @Override
    public void step(SimState simState) {
        LBVIEnvironment eState = (LBVIEnvironment) simState;
        //Collect population data once per year on the LAST day of the breeding year (day 363 of the 364-day
        //year; getSteps() % 364 == 363). By this day every agent has completed its end-of-season winter reset
        //(which fires around day ~219-220 and is what increments nSingles), so all counters for the year are
        //final, while nothing has touched them since ~day 220 (no breeding or births). Recording here — rather
        //than at day 0 of the next year — lets us label the row with the current year directly, with no
        //off-by-one, and still guarantees we run after every reset (so nSingles is complete).
        if (eState.schedule.getSteps() % 364 == 363) {
            collectPopData(eState);
            reset(eState);
        }
        //Stop once the configured number of years has run (1 year = 364 steps). The run covers steps
        //0 .. (years*364 - 1); the final year's summary is collected above on that last step (day 363) and
        //then we kill in the same step, so every configured year is logged exactly once.
        if (eState.schedule.getSteps() >= (long) eState.mpInputSimulationDurationYears * 364L - 1L) {
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
        // Collection runs on the last day of the breeding year (day 363), still within the current year, so
        // label this summary with currentYear directly (no off-by-one). populationSize is the overwinter-
        // survivor population entering next year, since interannual mortality has already been applied at the
        // ~day 219-220 resets. With the 30-year default you get clean rows for years 1-30.
        String popInfo = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", eState.currentYear,
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
