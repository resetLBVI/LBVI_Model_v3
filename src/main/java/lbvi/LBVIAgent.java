package lbvi;

import lbvi.Utils.ArrivalDate;
import lbvi.Utils.DispersalKernal;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;

import java.util.*;


public class LBVIAgent implements Steppable {
    //Identification and personal information
    int vireoID; //this is a unique ID for each Vireo agent, the ID is given when it survives through the first winter
    boolean vireoSexFemale; //if true, female. Otherwise, male
    boolean vireoAgeClassAdult; //if true, it's an adult; Otherwise, a first-year young
    int vireoAgeYears; //current age in years
    //Locations
    int vireoStartingLocation; //the starting location is a territoryID
    int vireoPreviousLocation; //the previous location is a territoryID
    int vireoCurrentLocation; //the current location is a territoryID
    double vireoCurrentX; //provide the Vireo current x coordinates, in meters, based on the patchID of currentLocation
    double vireoCurrentY; //provide the Vireo current y coordinates, in meters, based on the patchID of currentLocation
    //Arrival and start the breeding season
    int vireoArrivalDate; //determine the arrival date at the breeding site
    //reproductive state variables
    Stage vireoReproStage; //ten stages for both sexes
    boolean vireoMateStatus; //if mated, true; otherwise, false
    int vireoMateID; //identify the ID of an agent's mate
    int vireoNumNestAttempts; //female's state variable, indicate the number of nests in the current breeding season
    boolean hasNestedThisYear; //female's state variable: true once she builds her first nest this season (for NPairs = unique nesting females)
    int vireoNestID; //female's state variable, indicate current Nest ID
    int vireoNumVireoEggs; //female's state variable, indicate the number of Vireo eggs in current attempt
    int vireoNumCowbirdEggs; //female's state variable, indicate the number of Cowbird eggs after being parasitized
    int vireoNumVireoNestlings; //female's state variable, indicate the number of nestlings in current attempt
    int vireoNumCowbirdNestlings; //female's state variable, indicate the number of Cowbird nestlings after being parasitized
    int vireoNumVireoFledglings; //female's state variable, indicate the number of vireo fledglings in current attempt
    int vireoNumCowbirdFledglings; //female's state variable, indicate the number of Cowbird fledglings after being parasitized by Cowbirds
    int vireoNumVireoToIndependence; //female's state variable, indicate the number of Vireo after leaving the nest
    int vireoNumCowbirdToIndependence;//female's state variable, indicate the number of Cowbird after leaving the nest
    int vireoNumRecruits; //female's state variable, indicate the number of babies in current breeding season
    int vireoNumNests; //number of nests the female agents have in a year
    ArrayList<Integer> vireoRecruitList; //collect and record the current offspring IDs
    //Dispersal & Nest Patch Selection
    double vireoDispDir;
    double vireoDispDist;
    ArrayList<Integer> potentialTerritoryList; //determine a potential patch list for dispersal
    int potentialMateID; //record the potential mate
    //Nest state variables
    int nestID; //a unique ID for each nest
    int nestMaxNVireoEggs; //the maximum number of vireo eggs that can be laid in the nest
    int vireoNestTerrID; // = female vrieoCurrentLocation
    int nestCountdownToHatch; //
    int nestCountdownToFledging;
    int nestCountdownToIndependence;
    int nestCountdownToRenesting;
    int vireoCurrentNumAttempts;
    int lastReproResetYear = -1; // last currentYear in which this agent ran its end-of-season winter reset; guards against >1 reset/year
    // LOG-ON-CHANGE debug mode (LBVIEnvironment.debugLogOnChange): the non-time columns of the last debug row
    // actually written for this agent. null until the first row is written; compared each step so a state that
    // hasn't moved since the last row is not re-logged. Unused when debugLogOnChange is false.
    private String lastLoggedDebugFields = null;
    Nest currentNest; // reference to the active Nest object; null when no nest is active
    //other scheduling variables
    // Time is not stored on the agent; it reads the canonical clock on LBVIEnvironment
    // (currentJulianDay / currentYear), which LBVITimer advances once per step before agents run.
    Stoppable event; //allow the removal of an agent from the shcedule



    /*
    The constructor in JAVA is a special method that share the same name as its class and is used to initialize objects of that class. In this constructor
    define the identification and personal information and all the state variables when starting a LBVI agent
     */
    public LBVIAgent(LBVIEnvironment state, int vireoID, boolean vireoSexFemale, boolean vireoAgeClassAdult,
                     int vireoStartingLocation, boolean startup) {
        this.vireoID = vireoID; //a unique ID for each Vireo agent, the ID is given when it survives through the first winter
        this.vireoSexFemale = vireoSexFemale; //if true, female. Otherwise, male
        this.vireoAgeClassAdult = vireoAgeClassAdult; //if true, it's an adult; Otherwise, a first-year young
        if(startup) { //at startup, draw age from the sex-specific Leslie-matrix distribution (ages 1-based)
            this.vireoAgeYears = state.sampleStartupAge(vireoSexFemale);
        } else {
            this.vireoAgeYears = 0; //born in-sim: starts at age 0
    }
        this.vireoStartingLocation = vireoStartingLocation; //the starting location is a terrID
        this.vireoPreviousLocation = vireoStartingLocation; //the previous location is a terrID
        this.vireoCurrentLocation = vireoStartingLocation; //the current location is a terrID
        this.vireoArrivalDate = -1; //the arrival date was not defined before the breeding season starts
        this.vireoReproStage = Stage.ARRIVAL; //the first stage is "ARRIVAL" stage
        this.potentialTerritoryList = new ArrayList<>(); //create a new list
        this.potentialMateID = -1; // default no potential mate
        this.vireoMateStatus = false; //no mate yet
        this.vireoCurrentNumAttempts = 0; //no attempt in the beginning
        if(this.vireoSexFemale) { //only females track below state variables
            this.vireoNumNestAttempts = 0; //current number of nest attempts
            this.hasNestedThisYear = false; //has not built a nest yet this season
            this.vireoNestID = 0; //indicate current Nest ID
            this.vireoNumVireoEggs = 0; //current number of the eggs in the nest
            this.vireoNumVireoNestlings = 0; //current number of the nestlings in the nest
            this.vireoNumVireoFledglings = 0; //current number of the fledglings in the nest
            this.vireoNumRecruits = 0; //the number of babies in current breeding season
            this.vireoRecruitList = new ArrayList<>(); //add the babies' IDs in the list
            //nest related
            this.currentNest = null;
            this.nestMaxNVireoEggs = 0;
            this.vireoNestTerrID = 0; //when nest is built, the nestPatchName = female vireoCurrentLocation
            this.nestCountdownToHatch = state.mpIncubationStageDuration;
            this.nestCountdownToFledging = state.mpNestlingStageDuration;
            this.nestCountdownToIndependence = state.mpFledglingStageDuration;
            this.nestCountdownToRenesting = state.mpRenestingIntervalDuration;
        }
        potentialTerritoryList = new ArrayList<>();
        vireoMateID = 0; //after parining, note spouse's ID for it's mate
    }

    @Override
    public void step(SimState state) {
        LBVIEnvironment eState = (LBVIEnvironment) state;
        // Time comes from the canonical clock (LBVITimer updated it at ordering -1, before this agent runs).
        Stage currentStage = this.vireoReproStage;
        //DEBUG: log each agent's stage at the start of every step (logDebug.txt)
        logDebugState(eState);
        switch (currentStage) {
            case ARRIVAL: //Stage 1
                checkArrival(eState);
                break;
            case DISPERSAL: //Stage 2
                //check if there is enough time for nesting
                if (eState.currentJulianDay >= endOfSeasonDate(eState)) {
                    // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
                    if (eState.debugLog) {
                        eState.logDispersalOutcomeWriter.addToFile(String.format("%d,%d,%d,%s,%s,TIMEGATE,%b",
                                eState.currentYear, eState.currentJulianDay, vireoID,
                                vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile",
                                this.potentialTerritoryList.isEmpty()));
                    }
                    // END TEMP DIAGNOSTIC
                    //if there is NO time for dispersal, actEndBreeding
                    this.vireoReproStage = Stage.NONBREEDING; //prepare for NON-Breeding
                    break; //skip everthing below, exit the switch
                }
                //make sure the potentialTerritoryList is not empty and move forward to the next stage
                if(this.potentialTerritoryList.isEmpty()) {
                    //(1) determine the dispersal direction and distance based on the dispersal kernel
                    dispersalKernel(eState); //return a distance and looking for a list of potential dispersal patches.
                    //(2) move to the new location and develop a potential patch list
                    for(int j=0; j< eState.mpVireoNumSearchAttempts; j++) {
                        if(this.potentialTerritoryList.isEmpty()) {
                            this.potentialTerritoryList = findPotentialTerritoryList(eState, 1 + j * eState.mpVireoSearchDistanceCoefficient);
                        } else { //if there are some potential territories already
                            this.vireoReproStage = Stage.NESTTERRSELECT;
                            //record the list
                        }
                    }
                    // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
                    if (eState.debugLog && this.vireoReproStage == Stage.NESTTERRSELECT) {
                        eState.logDispersalOutcomeWriter.addToFile(String.format("%d,%d,%d,%s,%s,SETTLE,false",
                                eState.currentYear, eState.currentJulianDay, vireoID,
                                vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile"));
                    }
                    // END TEMP DIAGNOSTIC
                } else {
                    this.vireoReproStage = Stage.NESTTERRSELECT;
                    //record the list
                    // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
                    if (eState.debugLog) {
                        eState.logDispersalOutcomeWriter.addToFile(String.format("%d,%d,%d,%s,%s,SETTLE,false",
                                eState.currentYear, eState.currentJulianDay, vireoID,
                                vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile"));
                    }
                    // END TEMP DIAGNOSTIC
                }
                break;
            case NESTTERRSELECT: //Stage 3
                nestTerritorySelection(eState); //In this stage, agents choose a mate based on three criteria
                break;
            case PAIR: //Stage 4
                //check current location and find the Mr. or Mrs. Right!!
                //(1) locate current territory and find her mate
                if(this.vireoSexFemale) { //find Mr. Right
                    int mateID = eState.vegTerrInfo.get(this.vireoCurrentLocation).terrMaleID;
                    LBVIAgent spouse = eState.lbviAgentMap.get(mateID); //null when terrMaleID == -1 or the male is gone
                    if (spouse == null) {
                        // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
                        if (eState.debugLog) {
                            eState.logDispersalOutcomeWriter.addToFile(String.format("%d,%d,%d,%s,%s,PAIR_FAIL,0",
                                    eState.currentYear, eState.currentJulianDay, vireoID,
                                    vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile"));
                        }
                        // END TEMP DIAGNOSTIC
                        //No available male on this territory (mate-blind selection traits 0/4 can settle on an
                        //unoccupied territory, or the male died). Can't pair: release the territory and re-disperse.
                        releaseTerritory(eState);
                        this.potentialTerritoryList = new ArrayList<>();
                        this.vireoReproStage = Stage.DISPERSAL;
                        break;
                    }
                    // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
                    if (eState.debugLog) {
                        eState.logDispersalOutcomeWriter.addToFile(String.format("%d,%d,%d,%s,%s,PAIR_OK,0",
                                eState.currentYear, eState.currentJulianDay, vireoID,
                                vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile"));
                    }
                    // END TEMP DIAGNOSTIC
                    this.vireoMateID = mateID;
                    this.vireoMateStatus = true;
                    this.vireoReproStage = Stage.NESTBUILDING;
                    //update mate's status at the same time
                    spouse.vireoMateID = this.vireoID;
                    spouse.vireoMateStatus = true;
                    spouse.vireoReproStage = Stage.NESTBUILDING;
                    //NPairs is no longer incremented here. It now counts unique females that create at least one
                    //nest (counted on the first nest in nestBuilding), which matches the ODD definition, includes
                    //the initial pairs that start in NESTBUILDING without passing through PAIR, and avoids
                    //double-counting a female who re-pairs after a mate dies.
                } else {
                    //Bug A fix: a male parks at PAIR waiting for a female to settle on his territory. At season
                    //end send him into the NONBREEDING annual reset so he ages, releases his territory, and
                    //returns to ARRIVAL for next year. Previously males never reached NONBREEDING and froze here.
                    if (eState.currentJulianDay >= endOfSeasonDate(eState)) {
                        this.vireoReproStage = Stage.NONBREEDING;
                    }
                }
                break;
            case NESTBUILDING: //Stage 5
                if (this.vireoSexFemale) { //only females build nests
                    nestBuilding(eState);
                } else {
                    //Bug A fix: a paired male is parked in NESTBUILDING (only the female builds). At season end
                    //send him into the NONBREEDING annual reset instead of leaving him frozen here permanently.
                    if (eState.currentJulianDay >= endOfSeasonDate(eState)) {
                        this.vireoReproStage = Stage.NONBREEDING;
                    }
                }
                break;
            case EGGLAYING: //Stage 6
                if(this.vireoSexFemale) {
                    eggLaying(eState, this); //actEggLaying
                }
                break;
            case INCUBATION: //Stage 7
                if (this.vireoSexFemale) {
                    //If the nest is dead for any other reason, the female should terminate her current stage and start evaluating re-nesting
                    if (currentNest != null && currentNest.isEnded()) {
                        killCurrentNest(eState);
                        this.vireoReproStage = Stage.RENEST;
                        this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                        return;
                    }
                    if (this.vireoNumVireoEggs > 0 && this.nestCountdownToHatch > 0) {
                        if (state.random.nextBoolean(eState.mpDailyMortalityProbWholeNestIncubation)) { //whole nest predation event
                            logDailyMortalityForNest(eState, "predator-wholeNest", this.vireoNumVireoEggs+this.vireoNumCowbirdEggs, NESTStatus.EGG);
                            killCurrentNest(eState);
                            this.vireoReproStage = Stage.RENEST;
                            this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                            return;
                        }
                        //partial (per-egg) predation: one independent draw per egg present today.
                        //snapshot the counts first so the decrements don't shorten the loops.
                        int nVireoEggs   = this.vireoNumVireoEggs;
                        int nCowbirdEggs = this.vireoNumCowbirdEggs;
                        for (int i = 0; i < nVireoEggs; i++) { //one draw per Vireo egg
                            if (state.random.nextBoolean(eState.mpDailyMortalityProbEachEggIncubation)) {
                                this.vireoNumVireoEggs--;
                                logDailyMortalityForNest(eState, "predator-singleEgg", 1, NESTStatus.EGG);
                            }
                        }
                        for (int i = 0; i < nCowbirdEggs; i++) { //one draw per Cowbird egg
                            if (state.random.nextBoolean(eState.mpDailyMortalityProbEachEggIncubation)) {
                                this.vireoNumCowbirdEggs--;
                                logDailyMortalityForNest(eState, "predator-singleEgg", 1, NESTStatus.EGG);
                            }
                        }
                        //losing the last Vireo egg ends the nest (any remaining Cowbird eggs die with it)
                        if (this.vireoNumVireoEggs == 0) {
                            logDailyMortalityForNest(eState, "predator-wholeNest", this.vireoNumVireoEggs+this.vireoNumCowbirdEggs, NESTStatus.EGG);
                            killCurrentNest(eState);
                            this.vireoReproStage = Stage.RENEST;
                            this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                            return;
                        }
                        this.nestCountdownToHatch--;
                    } else if (this.vireoNumVireoEggs > 0 && this.nestCountdownToHatch == 0) { //complete incubation stage
                        this.vireoReproStage = Stage.NESTLING;
                        this.nestCountdownToFledging = eState.mpNestlingStageDuration;
                        vireoNumVireoNestlings = vireoNumVireoEggs;
                        vireoNumVireoEggs = 0;
                        eState.nNestlings += vireoNumVireoNestlings;
                        //log to the "successEvents.csv" output file
                        String incubationEvent = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s", state.schedule.getSteps(), eState.currentJulianDay, "hatch", this.vireoNestTerrID,
                                this.vireoID, this.nestID, this.vireoNumVireoNestlings, "nest", currentNest.getYoungSpecies(eState));
                        eState.logSuccessWriter.addToFile(incubationEvent);
                    }
                }
                break;
            case NESTLING: //Stage 8
                if (this.vireoSexFemale) {
                    if (currentNest != null && currentNest.isEnded()) {
                        killCurrentNest(eState);
                        this.vireoReproStage = Stage.RENEST;
                        this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                        return;
                    }
                    //There are still nestling in the nest
                    if (this.vireoNumVireoNestlings > 0 && this.nestCountdownToFledging > 0) { //whole nest predation event
                        //resolve the whole-nest rate ONCE for the day (baseline, or Cowbird-nestling-dependent
                        //under the hypothesis test) before the per-nestling counts change below.
                        double wholeNestRate = wholeNestMortalityDuringNestling(eState);
                        double perNestlingRate = nestlingMortalityDuringNestling(eState);
                        if (state.random.nextBoolean(wholeNestRate)) {
                            logDailyMortalityForNest(eState, "daily nest mortality", this.vireoNumVireoNestlings, NESTStatus.NESTLING);
                            killCurrentNest(eState);
                            this.vireoReproStage = Stage.RENEST;
                            this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                            return;
                        }
                        //partial (per-nestling) predation: one independent draw per nestling present today.
                        //snapshot the counts first so the decrements don't shorten the loops.
                        int nVireoNestlings   = this.vireoNumVireoNestlings;
                        int nCowbirdNestlings = this.vireoNumCowbirdNestlings;
                        for (int i = 0; i < nVireoNestlings; i++) { //one draw per Vireo nestling
                            if (state.random.nextBoolean(perNestlingRate)) {
                                this.vireoNumVireoNestlings--;
                                logDailyMortalityForNest(eState, "predator-singleNestling", 1, NESTStatus.NESTLING);
                            }
                        }
                        for (int i = 0; i < nCowbirdNestlings; i++) { //one draw per Cowbird nestling (baseline rate)
                            if (state.random.nextBoolean(eState.mpDailyMortalityProbEachNestling)) {
                                this.vireoNumCowbirdNestlings--;
                                logDailyMortalityForNest(eState, "predator-singleNestling", 1, NESTStatus.NESTLING);
                            }
                        }
                        //losing the last Vireo egg ends the nest (any remaining Cowbird nestlings die with it. 2026-08-17)
                        if (this.vireoNumVireoNestlings == 0) {
                            logDailyMortalityForNest(eState, "predator-wholeNest", this.vireoNumVireoNestlings+this.vireoNumCowbirdNestlings, NESTStatus.NESTLING);
                            killCurrentNest(eState);
                            this.vireoReproStage = Stage.RENEST;
                            this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                            return;
                        }
                        this.nestCountdownToFledging--;
                    } else if (this.vireoNumVireoNestlings > 0 && this.nestCountdownToFledging == 0) { //complete nestling stage
                        this.vireoReproStage = Stage.FLEDGLING;
                        this.nestCountdownToIndependence = eState.mpFledglingStageDuration;
                        vireoNumVireoFledglings = vireoNumVireoNestlings;
                        vireoNumVireoNestlings = 0;
                        eState.nFledglings += vireoNumVireoFledglings;
                        //log to the "successEvents.csv" output file
                        String nestlingEvent = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s", state.schedule.getSteps(), eState.currentJulianDay, "fledge", this.vireoNestTerrID,
                                this.vireoID, this.nestID, this.vireoNumVireoFledglings, "nest", currentNest.getYoungSpecies(eState));
                        eState.logSuccessWriter.addToFile(nestlingEvent);

                    }
                }
                break;
            case FLEDGLING: //Stage 9
                if (this.vireoSexFemale) {
                    if (this.vireoNumVireoFledglings > 0 && this.nestCountdownToIndependence > 0) {
                        //Now the fledglings have left the nest, so the mortality is for each individual predation
                        //one independent draw per fledgling present today.
                        //resolve the per-Vireo-fledgling rate ONCE (baseline, or Cowbird-fledgling-dependent under
                        //the hypothesis test) before the counts change. Cowbird fledglings use the baseline rate.
                        double vireoFledglingRate = fledglingMortalityPredator(eState);
                        //snapshot the counts first so the decrements don't shorten the loops.
                        int nVireoFledglings   = this.vireoNumVireoFledglings;
                        int nCowbirdFledglings = this.vireoNumCowbirdFledglings;
                        for (int i = 0; i < nVireoFledglings; i++) { //one draw per Vireo fledgling
                            if (state.random.nextBoolean(vireoFledglingRate)) {
                                this.vireoNumVireoFledglings--;
                                logDailyMortalityForNest(eState, "predator-singleFledgling", 1, NESTStatus.FLEDGLING);
                            }
                        }
                        for (int i = 0; i < nCowbirdFledglings; i++) { //one draw per Cowbird fledgling (baseline rate)
                            if (state.random.nextBoolean(eState.mpDailyMortalityProbEachFledgling)) {
                                this.vireoNumCowbirdFledglings--;
                                logDailyMortalityForNest(eState, "predator-singleFledgling", 1, NESTStatus.FLEDGLING);
                            }
                        }
                        //losing the last Vireo egg ends the nest (any remaining Cowbird eggs die with it)
                        if (this.vireoNumVireoFledglings == 0) {
                            logDailyMortalityForNest(eState, "predator-allFledglings", 0, NESTStatus.FLEDGLING);
                            killCurrentNest(eState);
                            this.vireoReproStage = Stage.RENEST;
                            this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                            return;
                        }
                        this.nestCountdownToIndependence--;
                    } else if (this.vireoNumVireoFledglings > 0 && this.nestCountdownToIndependence == 0) { //complete an attempt successfully
                        this.vireoCurrentNumAttempts++;
                        //male's vireoCurrentNumAttempts also need to be updated
                        //mate.vireoCurrentNumAttempts ++;
                        eState.nFledIndipendence += this.vireoNumVireoFledglings;
                        eState.nSuccNests++;
                        // create recruits for each fledgling that reached independence
                        for (int f = 0; f < this.vireoNumVireoFledglings; f++) {
                            LBVIAgent recruit = reproduceAgent(eState, this);
                            eState.lbviAgentMap.put(recruit.vireoID, recruit);
                            sim.util.Int2D loc = eState.territoryLocations.get(this.vireoCurrentLocation);
                            if (loc != null) eState.vegetationGrid.setObjectLocation(recruit, loc.x, loc.y);
                        }
                        //record the data to logSuccessEvent.csv (before zeroing vireoNumFledglings)
                        String fledgingEvent = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s", state.schedule.getSteps(), eState.currentJulianDay,
                                "fledgling independence", this.vireoNestTerrID, this.vireoID, this.nestID,
                                this.vireoNumVireoFledglings, "nest", currentNest.getYoungSpecies(eState));
                        eState.logSuccessWriter.addToFile(fledgingEvent);
                        killCurrentNest(eState); // nest lifecycle complete
                        this.vireoReproStage = Stage.RENEST;
                        this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration;
                    }
                }
                break;
            case RENEST: //Stage 10 evaluate before re-nest
                //evaluate if there is enough time for re-nesting
                if (eState.currentJulianDay + eState.mpIncubationStageDuration + eState.mpNestlingStageDuration + eState.mpFledglingStageDuration + eState.mpRenestingIntervalDuration > eState.mpLastPossibleNestingDate) {
                    //if there is NO time for re-nesting
                    //actEndBreeding
                    this.vireoReproStage = Stage.NONBREEDING; //prepare for NON-Breeding
                } else { //if there is enough time for re-nesting <= mpLastPossibleNestingDate
                    if(nestCountdownToRenesting > 0){
                        nestCountdownToRenesting--; //count down to renesting
                    } else { //go to renesting stage next step
                        this.nestCountdownToRenesting = eState.mpRenestingIntervalDuration; // reset for next attempt
                        this.vireoReproStage = Stage.NESTBUILDING; // create a fresh nest for the new attempt
                    }
                }
                break;
            case NONBREEDING: //Stage 11
                //End-of-season winter reset. Fire ONCE per agent per year, when either the breeding season has
                //ended (currentJulianDay >= mpLastPossibleNestingDate) or the agent is worn out from reaching
                //mpMaxNumAttempts. The lastReproResetYear guard prevents the reset (interannual mortality, aging,
                //nSingles count) from firing on every step the agent idles in NONBREEDING. (The previous
                //'|| vireoReproStage == NONBREEDING' clause was always true here and caused that repeated reset,
                //inflating nSingles/nNests via within-year recycling.)
                if ((eState.currentJulianDay >= endOfSeasonDate(eState)
                        || this.vireoCurrentNumAttempts >= eState.mpMaxNumAttempts)
                        && this.lastReproResetYear < eState.currentYear) {
                    this.lastReproResetYear = eState.currentYear;
                    //logReproductivePerformance.csv
                    //(1) NumPair (2) NumNest (3) NumEggs (4) NumNestlings (5) NumFledglings (6) NumIndependentFledglings (7) NumSuccessfulNests
                    //Count as a "single" only a breeding-eligible ADULT that never mated this year. Newborns now
                    //sit out their natal year in NONBREEDING (Bug C fix) and are not eligible, so they must not be
                    //counted here (this check runs before interannualMortality ages them up to adult below).
                    if (!this.vireoMateStatus && this.vireoAgeClassAdult) {
                        eState.nSingles++;
                    }
                    //update a bunch of Vireo state variables
                    if (deathByOldAge(eState)) {
                        break; // died of old age this year; skip the winter-survival draw
                    }
                    interannualMortality(eState);
                }
                break;
        }
    }

    /**
     * DEBUG: writes this agent's per-step state to logDebug.txt.
     * Header (LBVIEnvironment): "currentStep", "Year", "Date", "vireoID", "sex", "ageClass",
     * "LBVIStage", "arrivalDate", "currentLoc", "potentialTerrCount".
     * Compare arrivalDate against Date to confirm the ARRIVAL→DISPERSAL gate; a stuck agent
     * with an empty potentialTerrCount is bouncing NESTTERRSELECT→DISPERSAL with no eligible territory.
     * Rows are suppressed for the pre-arrival window (stage ARRIVAL) and for the post-reset NONBREEDING
     * idle; see the two early-return guards below.
     * Two modes, selected by LBVIEnvironment.debugLogOnChange: FULL (false, default) writes one row every
     * active step; LOG-ON-CHANGE (true) writes a row only when the agent's state-defining columns change
     * since the last row written for it, collapsing steps where only the clock moved.
     */
    private void logDebugState(LBVIEnvironment state) {
        if (!state.debugLog) return;                        // debug logging disabled
        if (this.vireoReproStage == Stage.ARRIVAL) return;  // skip pre-arrival rows (agent hasn't arrived yet)
        // Skip post-reset NONBREEDING idle rows. Once the winter reset has fired this year, lastReproResetYear
        // equals currentYear and every later NONBREEDING step is just the timer ticking with no behavior. This
        // keeps exactly one NONBREEDING row per agent per year (the reset-transition step) and drops the ~200
        // identical idle rows that follow it, which is the bulk of the debug file.
        if (this.vireoReproStage == Stage.NONBREEDING && this.lastReproResetYear >= state.currentYear) return;
        // Occupancy of the agent's current territory: for a female stuck at PAIR, terrMaleID == -1 means no
        // male is on her territory, so she will bounce PAIR -> DISPERSAL. Filtering sex=M shows where lone
        // males sit (terrFemaleID == -1) so you can see whether any pairable males exist. -999 = no territory.
        VegInfoIdentifier terr = state.vegTerrInfo.get(this.vireoCurrentLocation);
        int terrMaleID   = (terr == null) ? -999 : terr.terrMaleID;
        int terrFemaleID = (terr == null) ? -999 : terr.terrFemaleID;
        // State-defining columns: everything EXCEPT the time columns (currentStep / Year / Date), which always
        // move and would defeat change-detection. vireoID is constant per agent, so including it here is harmless.
        String fields = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                vireoID, vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile",
                vireoReproStage, vireoArrivalDate, vireoCurrentLocation, potentialTerritoryList.size(),
                terrMaleID, terrFemaleID, vireoMateID, vireoMateStatus);
        // LOG-ON-CHANGE mode (compact): skip this row when nothing but the clock has moved since the last row
        // written for this agent. Full mode (debugLogOnChange == false) logs every active step unconditionally.
        if (state.debugLogOnChange && fields.equals(this.lastLoggedDebugFields)) return;
        this.lastLoggedDebugFields = fields;
        String debugRow = state.schedule.getSteps() + "," + state.currentYear + "," + state.currentJulianDay + "," + fields;
        state.debugWriter.addToFile(debugRow);
    }
    /*
    ****************************************************************************************************
    *                                      ARRIVAL stage method
    * **************************************************************************************************
     */

    /**
     * Day by which the breeding season is fully over: the last possible nest-start date plus a complete
     * nesting cycle (incubation + nestling + fledgling). Any agent still lingering at or after this day
     * (including a male parked in PAIR/NESTBUILDING) should enter the NONBREEDING annual reset.
     */
    private int endOfSeasonDate(LBVIEnvironment state) {
        return state.mpLastPossibleNestingDate + state.mpIncubationStageDuration
             + state.mpNestlingStageDuration + state.mpFledglingStageDuration;
    }

    /**
     * The checkArrival method determine the arrival date if the agent hasn't detemined its arrival date for this breeding season
     * If the currentStep is larger than the arrival date, the agent move to the previous year location
     * @param state
     */
    public void checkArrival(LBVIEnvironment state) {
        //determine the arrival date if it haven't been determined
        if(vireoArrivalDate < 0 ) {
            vireoArrivalDate = ArrivalDate.arrivalDate(state.lowerArrivalDate, state.modeArrivalDate, state.upperArrivalDate);
        }
        //Bug B fix: only "arrive" within the spring arrival window [arrivalDate, upperArrivalDate]. The annual
        //reset puts a surviving bird back into ARRIVAL late in the season (~day 219); without the upper bound it
        //would arrive immediately, disperse, hit the dispersal time-gate, and bounce into NONBREEDING for the
        //rest of its life. With the bound it waits at ARRIVAL until currentJulianDay wraps to 0 at the year
        //boundary and then arrives normally during the next year's window.
        if(state.currentJulianDay >= vireoArrivalDate && state.currentJulianDay <= state.upperArrivalDate) {
            this.vireoCurrentLocation = this.vireoPreviousLocation;
            this.vireoReproStage = Stage.DISPERSAL;
        }
    }

    /*
     ****************************************************************************************************
     *                                      DISPERSAL stage method
     * **************************************************************************************************
     */

    public void dispersalKernel(LBVIEnvironment state) {
        vireoDispDir = Math.random() * 2 * Math.PI; //math.random() returns a double in range (0.0, 1.0) 2*Math.PI giving us a random angle in radians
        vireoDispDist = 0; //initiate the distance variable
        if(vireoSexFemale == true && vireoAgeClassAdult == true) { //Female Adult
            if (state.random.nextBoolean(state.mpVireoLongDistanceDispersal)) { //long dispersal distance
                vireoDispDist = state.random.nextDouble() * (state.mpVireoUpperCutoffLDD - state.mpVireoLowerCutoffLDD) + state.mpVireoLowerCutoffLDD;
            } else {//regular dispersal distance
                //Use Inverse Gaussian Function to generate a random dispersal distance; mu=2.66, lambda=0.33
                //Kernel returns KM; territory coordinates are in METRES, so convert km -> m (x1000).
                vireoDispDist = DispersalKernal.sampleInverseGaussian_SSJ(2.66, 0.33, state.random) * 1000.0;
            }
        } else if(vireoSexFemale == false && vireoAgeClassAdult == true) { //Male Adult
            if (state.random.nextBoolean(state.mpVireoLongDistanceDispersal)) {
                vireoDispDist = state.random.nextDouble() * (state.mpVireoUpperCutoffLDD - state.mpVireoLowerCutoffLDD) + state.mpVireoLowerCutoffLDD;
            } else { //regular dispersal distance
                //Use 2Dt Function to generate a random dispersal distance; a=0.078, b=1.47
                //Kernel returns KM; territory coordinates are in METRES, so convert km -> m (x1000).
                vireoDispDist = DispersalKernal.sampleBivariateT(0.078, 1.47, state.random) * 1000.0;
            }
        } else if (vireoSexFemale == true && vireoAgeClassAdult == false) { //Female Juveniles
            if (state.random.nextBoolean(state.mpVireoLongDistanceDispersal)) {
                vireoDispDist = state.random.nextDouble() * (state.mpVireoUpperCutoffLDD - state.mpVireoLowerCutoffLDD) + state.mpVireoLowerCutoffLDD;
            } else { //regular dispersal distance, Weibull, 5.49, 1.22
                //Use Weibull distribution Function to generate a random dispersal distance; scale_a=5.49, shape_b=1.22
                //Kernel returns KM; territory coordinates are in METRES, so convert km -> m (x1000).
                vireoDispDist = DispersalKernal.sampleWeibull(1.22, 5.49, state.random) * 1000.0;
            }
        } else { //Male Juveniles, 2dt, 3.33, 2.41
            if (state.random.nextBoolean(state.mpVireoLongDistanceDispersal)) {
                vireoDispDist = state.random.nextDouble() * (state.mpVireoUpperCutoffLDD - state.mpVireoLowerCutoffLDD) + state.mpVireoLowerCutoffLDD;
            } else { //regular dispersal distance, 2dt, 3.33, 2.41
                //Use 2Dt Function to generate a random dispersal distance; a=3.33, b=2.41
                //Kernel returns KM; territory coordinates are in METRES, so convert km -> m (x1000).
                vireoDispDist = DispersalKernal.sampleBivariateT(3.33, 2.41, state.random) * 1000.0;
            }
        }
    }

    public ArrayList<Integer> findPotentialTerritoryList(LBVIEnvironment state, double coefficient) {
        //(1) current location info
        VegInfoIdentifier currentTerritory = state.vegTerrInfo.get(vireoCurrentLocation); //currentLocation is a terrID, which is the index
        if (currentTerritory == null) {
            System.err.println("findPotentialTerritoryList: no vegInfo for terrID = " + currentTerritory);
            return potentialTerritoryList;
        }
        //current location POINT_X and POINT_Y from shapefile
        vireoCurrentX = currentTerritory.terrCoordX;
        vireoCurrentY = currentTerritory.terrCoordY;
        //(2) new location after dispersal
        double newVireoX = vireoCurrentX + vireoDispDist * Math.cos(vireoDispDir);
        double newVireoY = vireoCurrentY + vireoDispDist * Math.sin(vireoDispDir);
        //(3) define buffer zone around new location
        //define buffer zone
        double xLowerBound = newVireoX - coefficient * state.mpVireoTerrSearchDistanceM;
        double yLowerBound = newVireoY - coefficient * state.mpVireoTerrSearchDistanceM;
        double xUpperBound = newVireoX + coefficient * state.mpVireoTerrSearchDistanceM;
        double yUpperBound = newVireoY + coefficient * state.mpVireoTerrSearchDistanceM;
        //(4) loop through all territories from vegInfo
        for (VegInfoIdentifier p: state.vegTerrInfo.values()) {
            double px = p.terrCoordX;
            double py = p.terrCoordY;
            //check if territory center is inside the rectangle
            if(xLowerBound < px && px < xUpperBound && yLowerBound < py && py < yUpperBound) {
                potentialTerritoryList.add(p.terrID);
            }
        }
        // (5) exclude unsuitable territories from potentialTerritoryList
        if (!potentialTerritoryList.isEmpty()) {
            for (int i = potentialTerritoryList.size()-1; i>=0; i--) {
                VegInfoIdentifier terr = state.vegTerrInfo.get(potentialTerritoryList.get(i));
                if (!this.vireoSexFemale && terr.terrMaleID != -1) { //this is a male agent, and this territory already has another male
                    //exclude this territory from potentialList
                    potentialTerritoryList.remove(i);
                } else if (this.vireoSexFemale && terr.terrFemaleID != -1) { //this is a female agent, and this territory already has another female
                    //exclude this territory from potentialList
                    potentialTerritoryList.remove(i);
                } else if (terr.terrQuality == 0) {
                    //exclude this territory from potentialList
                    potentialTerritoryList.remove(i);
                }
            }
        }
        return potentialTerritoryList;
    }

    /*
This method decides which patch a vireo will choose as its nesting site. The decision depends on the bird’s patch
selection trait, which can follow one of three strategies:
(1) habitat quality only,
(2) Mate availability then rank habitat quality
(3) Mate availability only
(4) Randomly choose one territory from the potential list
Once the patch is chosen, the bird updates its location and changes stage to PAIR, meaning it’s ready to pair up
for breeding.
 */
    public void nestTerritorySelection(LBVIEnvironment state) {
        //capture the dispersal origin before a territory is chosen (vireoCurrentLocation is overwritten on selection)
        int dispersalOrigin = this.vireoCurrentLocation;
        //Step 1: remove territories that already has same-sex agent from potentialTerritoryList
        List<Integer> qualityRank;
        switch (state.mpTerrSelectionTrait) {
            case 0: //TRAIT 1 - habitat quality only
                qualityRank = VegetationChange.rankTerritoryByQuality(potentialTerritoryList, state.vegTerrInfo);
                if (qualityRank.isEmpty()) {
                    this.vireoReproStage = Stage.DISPERSAL;
                } else {
                    this.vireoCurrentLocation = qualityRank.get(0);
                    this.vireoReproStage = Stage.PAIR;
                }
                break;
            case 1: //TRAIT 2 - Mate availability only
                List<Integer> mateTerrList = this.vireoSexFemale
                        ? VegetationChange.queryTerrByPotentialMales(potentialTerritoryList, state.vegTerrInfo)
                        : VegetationChange.queryTerrByPotentialFemales(potentialTerritoryList, state.vegTerrInfo);
                if (potentialTerritoryList.isEmpty()) {
                    this.vireoReproStage = Stage.DISPERSAL;
                } else if (!mateTerrList.isEmpty()) {
                    this.vireoCurrentLocation = mateTerrList.get(state.random.nextInt(mateTerrList.size()));
                    this.vireoReproStage = Stage.PAIR;
                } else {
                    this.vireoCurrentLocation = potentialTerritoryList.get(state.random.nextInt(potentialTerritoryList.size()));
                    this.vireoReproStage = Stage.PAIR;
                }
                break;
            case 2: //TRAIT 3 - Rank habitat quality and then mate availability
                qualityRank = VegetationChange.rankTerritoryByQuality(potentialTerritoryList, state.vegTerrInfo);
                if (qualityRank.isEmpty()) {
                    this.vireoReproStage = Stage.DISPERSAL;
                } else {
                    int topQuality = state.vegTerrInfo.get(qualityRank.get(0)).terrQuality;
                    ArrayList<Integer> topTied = new ArrayList<>();
                    for (Integer id : qualityRank) {
                        if (state.vegTerrInfo.get(id).terrQuality == topQuality) {
                            topTied.add(id);
                        } else {
                            break;
                        }
                    }
                    if (topTied.size() > 1) {
                        List<Integer> mateInTied = this.vireoSexFemale
                                ? VegetationChange.queryTerrByPotentialMales(topTied, state.vegTerrInfo)
                                : VegetationChange.queryTerrByPotentialFemales(topTied, state.vegTerrInfo);
                        if (!mateInTied.isEmpty()) {
                            this.vireoCurrentLocation = mateInTied.get(state.random.nextInt(mateInTied.size()));
                        } else {
                            this.vireoCurrentLocation = topTied.get(state.random.nextInt(topTied.size()));
                        }
                    } else {
                        this.vireoCurrentLocation = topTied.get(0);
                    }
                    this.vireoReproStage = Stage.PAIR;
                }
                break;
            case 3: //TRAIT 4 - Mate availability and then habitat quality
                List<Integer> mateTerrs = this.vireoSexFemale
                        ? VegetationChange.queryTerrByPotentialMales(potentialTerritoryList, state.vegTerrInfo)
                        : VegetationChange.queryTerrByPotentialFemales(potentialTerritoryList, state.vegTerrInfo);
                if (potentialTerritoryList.isEmpty()) {
                    this.vireoReproStage = Stage.DISPERSAL;
                } else if (!mateTerrs.isEmpty()) {
                    qualityRank = VegetationChange.rankTerritoryByQuality(new ArrayList<>(mateTerrs), state.vegTerrInfo);
                    double topMateQuality = state.vegTerrInfo.get(qualityRank.get(0)).terrQuality;
                    ArrayList<Integer> topMateTied = new ArrayList<>();
                    for (Integer id : qualityRank) {
                        if (state.vegTerrInfo.get(id).terrQuality == topMateQuality) {
                            topMateTied.add(id);
                        } else {
                            break;
                        }
                    }
                    this.vireoCurrentLocation = topMateTied.get(state.random.nextInt(topMateTied.size()));
                    this.vireoReproStage = Stage.PAIR;
                } else {
                    this.vireoCurrentLocation = potentialTerritoryList.get(state.random.nextInt(potentialTerritoryList.size()));
                    this.vireoReproStage = Stage.PAIR;
                }
                break;
            case 4: //TRAIT 4 - totally random
                if (potentialTerritoryList == null || potentialTerritoryList.size() == 0) {
                    this.vireoReproStage = Stage.DISPERSAL;
                } else {
                    this.vireoCurrentLocation = potentialTerritoryList.get(state.random.nextInt(potentialTerritoryList.size()));
                    this.vireoReproStage = Stage.PAIR;
                }
                break;
        }
        //a territory was chosen this step (stage advanced to PAIR): record the realized dispersal
        if (this.vireoReproStage == Stage.PAIR) {
            this.vireoPreviousLocation = dispersalOrigin;
            claimTerritory(state); //mark the territory occupied so others can find a mate / avoid it
            logDispersalDistance(state, dispersalOrigin);
        }
        // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
        if (state.debugLog) {
            if (this.vireoReproStage == Stage.PAIR) {
                state.logDispersalOutcomeWriter.addToFile(String.format("%d,%d,%d,%s,%s,NESTTERR_PAIR,%d",
                        state.currentYear, state.currentJulianDay, vireoID,
                        vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile",
                        this.potentialTerritoryList.size()));
            } else if (this.vireoReproStage == Stage.DISPERSAL) {
                state.logDispersalOutcomeWriter.addToFile(String.format("%d,%d,%d,%s,%s,NESTTERR_REJECT,%d",
                        state.currentYear, state.currentJulianDay, vireoID,
                        vireoSexFemale ? "F" : "M", vireoAgeClassAdult ? "Adult" : "Juvenile",
                        this.potentialTerritoryList.size()));
            }
        }
        // END TEMP DIAGNOSTIC
    }

    /**
     * Marks this agent's selected territory (vireoCurrentLocation) as occupied by writing this agent's
     * vireoID into terrMaleID (male) or terrFemaleID (female). This is what makes the same-sex
     * exclusion in findPotentialTerritoryList and mate lookup (queryTerrByPotentialMales/Females,
     * PAIR stage) work. Released via releaseTerritory() on death and the annual reset.
     */
    private void claimTerritory(LBVIEnvironment state) {
        VegInfoIdentifier terr = state.vegTerrInfo.get(this.vireoCurrentLocation);
        if (terr == null) return;
        if (this.vireoSexFemale) {
            terr.terrFemaleID = this.vireoID;
        } else {
            terr.terrMaleID = this.vireoID;
        }
    }

    /**
     * Frees this agent's current territory (sets the relevant slot back to -1) but only if this agent
     * still holds it. Idempotent, so it is safe to call more than once per season.
     */
    private void releaseTerritory(LBVIEnvironment state) {
        VegInfoIdentifier terr = state.vegTerrInfo.get(this.vireoCurrentLocation);
        if (terr == null) return;
        if (this.vireoSexFemale && terr.terrFemaleID == this.vireoID) {
            terr.terrFemaleID = -1;
        } else if (!this.vireoSexFemale && terr.terrMaleID == this.vireoID) {
            terr.terrMaleID = -1;
        }
    }

    /**
     * Records a realized dispersal to logDispersalDistance.csv, aligned to logDispersalDistanceHeader:
     * "currentStep", "Date", "terrID", "vireoID", "PrevLoc", "Distance", "Sex", "AgeClass", "AgeYears"
     * Distance is the Euclidean distance (meters) between the previous territory's center and the
     * chosen territory's center, using terrCoordX/terrCoordY from vegTerrInfo.
     */
    private void logDispersalDistance(LBVIEnvironment state, int prevLoc) {
        VegInfoIdentifier from = state.vegTerrInfo.get(prevLoc);
        VegInfoIdentifier to = state.vegTerrInfo.get(this.vireoCurrentLocation);
        double distance = Math.hypot(to.terrCoordX - from.terrCoordX, to.terrCoordY - from.terrCoordY);
        String record = String.format("%s,%s,%s,%s,%s,%.4f,%s,%s,%d", state.schedule.getSteps(), state.currentJulianDay,
                this.vireoCurrentLocation, this.vireoID, prevLoc, distance,
                this.vireoSexFemale ? "F" : "M", this.vireoAgeClassAdult ? "Adult" : "Juvenile", this.vireoAgeYears);
        state.logDispersalDistanceWriter.addToFile(record);
    }
    /*
    ***********************************************************************************************
    *                   REPRODUCTION & PARENTAL CARE
    * *********************************************************************************************
     */

    /**
     * ODD - nest building (female-driven, Stage.NESTBUILDING). Building takes mpVireoNestBuildingDuration
     * days. On the first day the female creates and schedules a Nest; on each building day she makes a
     * "false start" draw (mpDailyProbNestDesertionBeforeEggLaying) - a false start abandons the nest (NOT
     * counted as a nesting attempt) and she starts a fresh nest the next day. Otherwise she counts down and,
     * at completion, advances to EGGLAYING. The Nest's status (BUILDING here) is derived from this stage, so
     * nothing is written back to the Nest.
     */
    private void nestBuilding(LBVIEnvironment state) {
        // First day of this build: create the nest (nestDaysToCompletion is set in the ctor). The clutch size
        // is NOT drawn yet — ODD 3.8 draws it when the nest finishes building (see the completion branch below).
        if (this.currentNest == null) {
            this.vireoNestTerrID = this.vireoCurrentLocation;
            int nestPatchID = state.vegTerrInfo.get(this.vireoCurrentLocation).patchID;
            this.nestID++;
            Nest newNest = new Nest(state, this.nestID, nestPatchID, this.vireoNestTerrID, this.vireoID);
            newNest.event = state.schedule.scheduleRepeating(newNest);
            this.currentNest = newNest;
            state.activeNests.add(newNest);
            state.nestMap.put(this.nestID, newNest);
            // NNests / NPairs are counted at nest COMPLETION (below), not at creation, so that false-start
            // rebuilds (abandoned before completion) are excluded while every completed attempt is counted.
        }
        // Daily false-start check: abandon before egg-laying and rebuild next day.
        if (state.random.nextBoolean(state.mpDailyProbNestDesertionBeforeEggLaying)) {
            logBuildingEvent(state, "false start", 0);
            abandonNestBeforeEggLaying(state);
            return;
        }
        // Count down the build; when it completes, move on to egg-laying.
        this.currentNest.nestDaysToCompletion--;
        if (this.currentNest.nestDaysToCompletion <= 0) {
            logBuildingEvent(state, "nest completion", 1);
            // NNests = total completed nests this year: includes every renesting attempt that completes building,
            // excludes false-start rebuilds (abandoned above before reaching completion).
            state.nNests++;
            // NPairs = unique females that completed at least one nest this year: count this female once, on her
            // first completed nest (includes the initial pairs; no double-count from renests or re-pairing).
            if (!this.hasNestedThisYear) {
                state.nPairs++;
                this.hasNestedThisYear = true;
            }
            // ODD 3.8: now that the nest is complete, draw its clutch size and record it on both the female
            // (used as the egg-laying cap at STEP 4) and the nest (for tracking). Drawing here — rather than at
            // creation — means false-started nests never consume a clutch-size draw.
            this.nestMaxNVireoEggs = getNestMaxNEggs(state);
            this.currentNest.nestMaxNVireoEggs = this.nestMaxNVireoEggs;
            this.vireoReproStage = Stage.EGGLAYING;
        }
    }

    /**
     * Logs a nest-building event ("false start" / "nest completion") to SuccessEvents.csv, matching the
     * egg/hatch/fledge row layout: step, Date, EventType, TerrID, VireoID, NestID, NumIndividual, EntityType, YoungSp.
     */
    private void logBuildingEvent(LBVIEnvironment state, String eventType, int numIndividual) {
        String row = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s", state.schedule.getSteps(), state.currentJulianDay,
                eventType, this.vireoNestTerrID, this.vireoID, this.nestID, numIndividual, "nest", this.currentNest.getYoungSpecies(state));
        state.logSuccessWriter.addToFile(row);
    }

    /**
     * Abandons a nest still under construction (false start): stops its schedule event, removes it from
     * global tracking, and clears the female's reference - WITHOUT logging a mortality, since a false start
     * is not a nesting attempt. The female stays in NESTBUILDING and builds a fresh nest the next day.
     */
    private void abandonNestBeforeEggLaying(LBVIEnvironment state) {
        if (this.currentNest == null) return;
        this.currentNest.markEnded();
        this.currentNest.event.stop();
        state.activeNests.remove(this.currentNest);
        state.nestMap.remove(this.currentNest.nestID);
        this.currentNest = null;
    }

    public void eggLaying(LBVIEnvironment state, LBVIAgent femaleAgent) {
        Nest nest = femaleAgent.currentNest; // the active nest carries the egg-loss / desertion flags
        if (nest == null) {
            return; // no active nest to lay into
        }

        // STEP 1 (ODD 3.10) - desertion check for a VIREO-egg loss that occurred on a PREVIOUS day, from
        // either predation or a cowbird (3.16). The day-stamp guard (< today) enforces the "between days"
        // rule regardless of whether the female or the cowbird stepped first today. Cowbird-egg changes do
        // not count - Vireos respond only to losses of their own eggs.
        if (nest.vireoEggLossDay >= 0 && nest.vireoEggLossDay < state.currentJulianDay) {
            String cause = nest.vireoEggLossCause;   // "egg depredation" | "cowbird"
            nest.vireoEggLossDay = -1;               // one-time trigger - consume it either way
            nest.vireoEggLossCause = null;
            if (state.random.nextBoolean(state.mpDailyProbNestDesertionAfterEggLoss)) {
                nest.nestDeath(state, "nest desertion due to " + cause);
                desertNestDuringEggLaying(state, femaleAgent);
                return;
            }
            // otherwise she ignores the loss and proceeds to lay
        }

        // STEP 2 (ODD 3.10) - lay one new Vireo egg.
        femaleAgent.vireoNumVireoEggs++;

        // STEP 3 (ODD 3.10) - one predation draw per egg now in the nest (Vireo and Cowbird alike).
        eggPredationDuringEggLaying(state, femaleAgent, nest);

        // STEP 4 (ODD 3.10) - clutch completion.
        if (femaleAgent.vireoNumVireoEggs >= femaleAgent.nestMaxNVireoEggs) {
            femaleAgent.vireoReproStage = Stage.INCUBATION;
            femaleAgent.nestCountdownToHatch = state.mpIncubationStageDuration;
            femaleAgent.vireoNumNestAttempts ++; //double check ODD with Casey
            state.nEggs += femaleAgent.vireoNumVireoEggs;
            //log SuccessEvents.csv, clutch completion
            String eggLayingEvent = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s", state.schedule.getSteps(), state.currentJulianDay, "clutch completion", femaleAgent.vireoNestTerrID,
                    femaleAgent.vireoID, femaleAgent.nestID, femaleAgent.vireoNumVireoEggs, "nest", femaleAgent.currentNest.getYoungSpecies(state));
            state.logSuccessWriter.addToFile(eggLayingEvent);
        }
    }

    /**
     * ODD 3.10 per-egg predation during egg-laying. After a new egg is laid, one independent draw against
     * mpDailyProbEggMortalityPredatorDuringEggLaying is made for EVERY egg present - Vireo and Cowbird.
     * Each destroyed egg is logged as an "egg depredation" event (with Vireo/Cowbird detail) and the
     * matching count is decremented. Losing one or more VIREO eggs stamps the nest for the following day's
     * desertion draw (3.10); losing only Cowbird eggs does not (Vireos ignore Cowbird-egg changes). Unlike
     * incubation, running out of Vireo eggs here does not end the nest immediately - she may lay again next
     * day if she does not desert.
     */
    private void eggPredationDuringEggLaying(LBVIEnvironment state, LBVIAgent femaleAgent, Nest nest) {
        double predationProb = state.mpDailyMortalityProbEachEggEggLaying;
        boolean vireoEggLost = false;
        // snapshot the counts first so the decrements don't shorten the loops
        int nVireoEggs   = femaleAgent.vireoNumVireoEggs;
        int nCowbirdEggs = femaleAgent.vireoNumCowbirdEggs;
        for (int i = 0; i < nVireoEggs; i++) {
            if (state.random.nextBoolean(predationProb)) {
                femaleAgent.vireoNumVireoEggs--;
                vireoEggLost = true;
                femaleAgent.logDailyMortalityForNest(state, "egg depredation-Vireo", 1, NESTStatus.EGG);
            }
        }
        for (int i = 0; i < nCowbirdEggs; i++) {
            if (state.random.nextBoolean(predationProb)) {
                femaleAgent.vireoNumCowbirdEggs--;
                femaleAgent.logDailyMortalityForNest(state, "egg depredation-Cowbird", 1, NESTStatus.EGG);
            }
        }
        if (vireoEggLost) {
            nest.vireoEggLossDay = state.currentJulianDay;   // ODD 3.10: next-day desertion draw
            nest.vireoEggLossCause = "egg depredation";
        }
    }

    /**
     * Cleans up the female's state after her nest is deserted mid-egg-laying (predation or cowbird):
     * nestDeath() has already stopped/removed the Nest, so this just clears the female's reference and
     * sends her to RENEST, mirroring the dead-nest handling in the INCUBATION/NESTLING/FLEDGLING stages.
     */
    private void desertNestDuringEggLaying(LBVIEnvironment state, LBVIAgent femaleAgent) {
        femaleAgent.killCurrentNest(state);
        femaleAgent.vireoReproStage = Stage.RENEST;
        femaleAgent.nestCountdownToRenesting = state.mpRenestingIntervalDuration;
    }
    /**
     * The reproduction action is executed when the young-of-
     * @param state
     * @param femaleAgent
     * @return
     */
    public LBVIAgent reproduceAgent(LBVIEnvironment state, LBVIAgent femaleAgent) {
        int newbornID = state.lbviAgentID ++;
        boolean newbornSex = state.random.nextBoolean(state.mpProbVireoIsFemale); //true = female, false = male
        LBVIAgent newAgent = new LBVIAgent(state, newbornID, newbornSex, false,
                femaleAgent.vireoCurrentLocation, false);
        //Bug C fix: a newborn must NOT breed in its natal year. Start it in NONBREEDING (overriding the
        //constructor's default ARRIVAL) so it sits out the current season; the end-of-season reset then draws
        //juvenile winter survival, ages it to adult, and returns it to ARRIVAL for next year's breeding window.
        //Previously recruits started in ARRIVAL and early-born ones dispersed, paired and nested the same year.
        newAgent.vireoReproStage = Stage.NONBREEDING;
        //J1 (ODD 3.20): record this offspring on its female parent. vireoNumRecruits is the per-season
        //count and vireoRecruitList holds the IDs; both are cleared each year in updateLBVIStateVariables.
        femaleAgent.vireoRecruitList.add(newbornID);
        femaleAgent.vireoNumRecruits++;
        newAgent.event = state.schedule.scheduleRepeating(newAgent);
        return newAgent;
    }
    /*
    *****************************************************************************************
    *                           MORTALITY & DEATH
    * ***************************************************************************************
     */
    //Agent death by old age: a hard cap at the sex-specific maximum age (F=12, M=21).
    //Returns true if the agent died, so the caller can skip further per-agent processing this step
    //(die() only stops future steps; it does not halt the current one).
    public boolean deathByOldAge (LBVIEnvironment state) {
        if (this.vireoAgeYears >= state.vireoMaxAge(this.vireoSexFemale)) {
            this.die(state);
            return true;
        }
        return false;
    }
    //Agent inter-annual Mortality
    public void interannualMortality(LBVIEnvironment state) {
        if (this.vireoAgeClassAdult == false && this.vireoSexFemale == true) { //female young-of-the-year
            if (state.random.nextBoolean(state.mpSurvivalProbJuvenileF)) { //the female young-of-the-year survived
                updateLBVIStateVariables(state);
            } else {
                //ODD 3.20: log "juvenile mortality" to logMortalityEvents.csv before removal
                logInterannualMortality(state, "juvenile mortality", "juvenile vireo");
                this.die(state); //this young-of-the-year does not survive through the winter
            }
        } else if (this.vireoAgeClassAdult == false && this.vireoSexFemale == false) { //male young-of-the-year
            if (state.random.nextBoolean(state.mpSurvivalProbJuvenileM)) {
                updateLBVIStateVariables(state);
            } else {
                //ODD 3.20: log "juvenile mortality" to logMortalityEvents.csv before removal
                logInterannualMortality(state, "juvenile mortality", "juvenile vireo");
                this.die (state); //this young-of-the-year doesnot survive through the winter
            }
        } else if (this.vireoAgeClassAdult == true && this.vireoSexFemale == true) { //female adult
            if (state.random.nextBoolean(state.mpSurvivalProbAdultF)) { //this adult female survive
                updateLBVIStateVariables(state);
            } else {
                //ODD 3.20: log "adult mortality" to logMortalityEvents.csv before removal
                logInterannualMortality(state, "adult mortality", "adult vireo");
                this.die(state); //female adult die during the winter
            }
        } else { //male adult
            if (state.random.nextBoolean(state.mpSurvivalProbAdultM)) { //this adult male survive
                updateLBVIStateVariables(state);
            } else {
                //ODD 3.20: log "adult mortality" to logMortalityEvents.csv before removal
                logInterannualMortality(state, "adult mortality", "adult vireo");
                this.die(state); //male adult die during the winter
            }
        }
    }

    /**
     * Logs an inter-annual (non-breeding season) Vireo mortality event to logMortalityEvents.csv,
     * aligned to logMortalityHeader:
     *   step, Date, EventType, TerrID, VireoID, NestID, CowbirdID, NumIndividual, EntityType, YoungSp
     * Per ODD section 3.20, over-winter deaths are recorded with EventType "juvenile mortality" or
     * "adult mortality" and EntityType "juvenile vireo" or "adult vireo", one row per bird
     * (NumIndividual = 1). These deaths are neither nest- nor cowbird-related and involve no nest
     * young, so NestID, CowbirdID and YoungSp are "NA"; TerrID is the bird's current location.
     * Call before die().
     */
    private void logInterannualMortality(LBVIEnvironment state, String eventType, String entityType) {
        String record = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                state.schedule.getSteps(), state.currentJulianDay, eventType,
                this.vireoCurrentLocation, this.vireoID, "NA", "NA", 1, entityType, "NA");
        state.logMortalityWriter.addToFile(record);
    }

    /*
    ***********************************************************************************************
    *                               HELPER METHODS
    * *********************************************************************************************
     */
    /**
     * Draws the clutch size (number of Vireo eggs to be laid in a completed nest) from the empirical
     * cumulative distribution mpVireoPopClutchSize, per ODD §3.8. The vector is a CDF over clutch sizes
     * starting at minClutch (2): entry i is P(clutch size <= 2 + i), so it must be non-decreasing with a
     * final entry of ~1.0. Draw U ~ Uniform(0,1) and return the first clutch size whose cumulative
     * probability exceeds U. If U falls past the last entry (e.g. a CDF that ends slightly below 1.0),
     * the largest clutch size is returned.
     */
    public int getNestMaxNEggs(LBVIEnvironment state) {
        final int minClutch = 2;                     // CDF index 0 -> 2 eggs
        double[] cdf = state.mpVireoPopClutchSize;
        if (cdf == null || cdf.length == 0) return minClutch;   // degenerate guard
        double u = state.random.nextDouble();        // U(0,1), seeded model RNG
        for (int i = 0; i < cdf.length; i++) {
            if (u < cdf[i]) return minClutch + i;     // first clutch size whose cumulative prob exceeds U
        }
        return minClutch + cdf.length - 1;           // fallback: largest clutch size
    }

    /**
     * Whole-nest predation probability for the current NESTLING-stage day.
     * Default model (mpTestCowbirdNestlingMortalityHypothesis == false): always the baseline
     * mpDailyNestlingMortality. Hypothesis model: the rate depends on how many Cowbird nestlings
     * share this nest — 0 → baseline, 1 → ...1BHCONestling, 2+ → ...2orMoreBHCONestlings.
     */
    private double wholeNestMortalityDuringNestling(LBVIEnvironment eState) {
        if (!eState.mpTestCowbirdNestlingMortalityHypothesis || this.vireoNumCowbirdNestlings == 0) {
            return eState.mpDailyMortalityProbWholeNestNestlingStage;
        } else if (this.vireoNumCowbirdNestlings == 1) {
            return eState.mpDailyMortalityProbWholeNestNestlingStage1BHCONestling;
        } else {
            return eState.mpDailyMortalityProbWholeNestNestlingStage2orMoreBHCONestlings;
        }
    }

    /**
     * Per-Vireo-nestling predation probability for the current NESTLING-stage day (one draw per Vireo
     * nestling). Default model (mpTestCowbirdNestlingMortalityHypothesis == false): always the baseline
     * mpDailyProbNestlingMortalityPredatorDuringNestling. Hypothesis model: the rate depends on how
     * many Cowbird nestlings share this nest — 0 → baseline, 1 → ...1BHCONestling, 2+ → ...2orMoreBHCONestling.
     * Note: this governs Vireo-nestling draws only; Cowbird nestlings always use the baseline rate.
     */
    private double nestlingMortalityDuringNestling(LBVIEnvironment eState) {
        if (!eState.mpTestCowbirdNestlingMortalityHypothesis || this.vireoNumCowbirdNestlings == 0) {
            return eState.mpDailyMortalityProbEachNestling;
        } else if (this.vireoNumCowbirdNestlings == 1) {
            return eState.mpDailyMortalityProbEachNestling1BHCONestling;
        } else {
            return eState.mpDailyMortalityProbEachNestling2orMoreBHCONestlings;
        }
    }

    /**
     * Per-Vireo-fledgling predation probability for the current FLEDGLING-stage day (one draw per
     * Vireo fledgling). Default model (mpTestCowbirdFledglingMortalityHypothesis == false): always the
     * baseline mpDailyProbFledglingMortalityPredator. Hypothesis model: the rate depends on how many
     * Cowbird fledglings share this brood — 0 → baseline, 1 → ...1BHCOFledgling, 2+ → ...2orMoreBHCOFledglings.
     * Note: this governs Vireo-fledgling draws only; Cowbird fledglings always use the baseline rate.
     */
    private double fledglingMortalityPredator(LBVIEnvironment eState) {
        if (!eState.mpTestCowbirdFledglingMortalityHypothesis || this.vireoNumCowbirdFledglings == 0) {
            return eState.mpDailyMortalityProbEachFledgling;
        } else if (this.vireoNumCowbirdFledglings == 1) {
            return eState.mpDailyMortalityProbEachFledgling1BHCOFledgling;
        } else {
            return eState.mpDailyMortalityProbEachFledgling2orMoreBHCOFledglings;
        }
    }

    // Logs a daily-mortality event to logMortalityEvents.csv, aligned to logMortalityHeader:
    // "step", "Date", "EventType", "TerrID", "VireoID", "NestID", "CowbirdID", "NumIndividual", "EntityType", "YoungSp"
    // CowbirdID is "NA" because these deaths are not cowbird-related. Call before killCurrentNest,
    // which clears currentNest (used here to read youngSpecies).
    private void logDailyMortalityForNest(LBVIEnvironment eState, String eventType, int numIndividual, NESTStatus entityType) {
        String youngSp = (currentNest != null) ? currentNest.getYoungSpecies(eState) : "none";
        String dailyMortality = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", eState.schedule.getSteps(), eState.currentJulianDay,
                eventType, this.vireoNestTerrID, this.vireoID, this.nestID, "NA", numIndividual, entityType, youngSp);
        eState.logMortalityWriter.addToFile(dailyMortality);
    }

    // Stops the active nest, removes it from global tracking, and clears the female's reference.
    // Safe to call regardless of which entity (female or Nest's TLB check) triggered the death.
    // All callers log any needed counts BEFORE calling this, so it also drains the female's young
    // counts here — this ends the nest cleanly and makes the derived getYoungSpecies() report "none"
    // until the next nest lays eggs.
    private void killCurrentNest(LBVIEnvironment eState) {
        if (currentNest == null) return;
        if (!currentNest.isEnded()) {
            currentNest.markEnded();
            currentNest.event.stop();
        }
        eState.activeNests.remove(currentNest);
        currentNest = null;
        resetYoungCounts();
    }

    // Zeroes every young count on this female (Vireo and Cowbird, across all three stages) so the
    // derived Nest.getYoungSpecies() returns "none". Called when a nest ends (killCurrentNest, which
    // covers both death and the drain before a re-nest) and at the annual reset.
    private void resetYoungCounts() {
        this.vireoNumVireoEggs = 0;
        this.vireoNumCowbirdEggs = 0;
        this.vireoNumVireoNestlings = 0;
        this.vireoNumCowbirdNestlings = 0;
        this.vireoNumVireoFledglings = 0;
        this.vireoNumCowbirdFledglings = 0;
    }

    public void die(LBVIEnvironment state) {
        //1. remove from their social network (parents, mateID, etc.)
        releaseTerritory(state); //free the territory this agent held so others can claim it
        //2. stop the event
        event.stop(); //remove the agent from the schedule
        state.vegetationGrid.remove(this);
        state.lbviAgentMap.remove(this.vireoID);
    }

    public void updateLBVIStateVariables(LBVIEnvironment state) {
        releaseTerritory(state); //give up this season's territory before resetting for next year
        this.vireoAgeYears ++;
        if (this.vireoAgeYears >= 1) {
            this.vireoAgeClassAdult = true;
        } else {
            this.vireoAgeClassAdult = false;
        }
        this.vireoStartingLocation = vireoCurrentLocation;
        this.vireoPreviousLocation = vireoCurrentLocation;
        this.vireoArrivalDate = -1;
        this.vireoReproStage = Stage.ARRIVAL;
        this.vireoMateStatus = false;
        this.vireoCurrentNumAttempts = 0;
        if(this.vireoSexFemale) { //only females track below state variables
            this.vireoNumNestAttempts = 0;
            this.hasNestedThisYear = false; //reset so she is counted once in NPairs when she first nests next year
            this.vireoNestID = 0;
            resetYoungCounts(); //zero all Vireo + Cowbird egg/nestling/fledgling counts for the new year
            this.vireoNumRecruits = 0;      //reset the per-season recruit count (J1)
            this.vireoRecruitList.clear();  //reset the per-season recruit ID list to stay consistent with the count
            //nest related
            this.nestID = 0;
            this.nestMaxNVireoEggs = 0;     //cleared; re-drawn at each nest completion (ODD 3.8), not at the annual reset
            this.vireoNestTerrID = 0;
            this.currentNest = null;
            this.nestCountdownToHatch = state.mpIncubationStageDuration;
            this.nestCountdownToFledging = state.mpNestlingStageDuration;
            this.nestCountdownToIndependence = state.mpFledglingStageDuration;
            this.nestCountdownToRenesting = state.mpRenestingIntervalDuration;
        }
        potentialTerritoryList = new ArrayList<>();
        vireoMateID = 0;
    }


    /* =========================================================
       ARCHIVED — kept for reference, not called by active code
       ========================================================= */

    @Deprecated
    public void dispersalKernel_old(LBVIEnvironment state) {
        vireoDispDir = Math.random() * 2 * Math.PI;
        vireoDispDist = 0;
        if(vireoSexFemale == true && vireoAgeClassAdult == true) { //Female Adult
            vireoDispDist = DispersalKernal.sampleInverseGaussian_SSJ(2.66, 0.33, state.random);
            if(vireoDispDist > state.vireoBeyondKernalFAd) {
                double[] arr = {24, 25, 29};
                Random random = new Random();
                vireoDispDist = arr[random.nextInt(arr.length)];
            }
        } else if(vireoSexFemale == false && vireoAgeClassAdult == true) { //Male Adult
            vireoDispDist = DispersalKernal.sampleBivariateT(0.078, 1.47, state.random);
            if(vireoDispDist > state.vireoBeyondKernalMAd) {
                double[] arr = {24, 24, 25, 35, 36, 40, 63, 72, 79, 84, 104, 158};
                Random random = new Random();
                vireoDispDist = arr[random.nextInt(arr.length)];
            }
        } else if (vireoSexFemale == true && vireoAgeClassAdult == false) { //Female Juveniles
            vireoDispDist = DispersalKernal.sampleWeibull(1.22, 5.49, state.random);
            if(vireoDispDist > state.vireoBeyondKernalFJu) {
                vireoDispDist = 0;
            }
        } else { //Male Juveniles
            vireoDispDist = DispersalKernal.sampleBivariateT(3.33, 2.41, state.random);
            if(vireoDispDist > state.vireoBeyondKernalMJu) {
                double[] arr = {22,23,25,26,61,89,97,105};
                Random random = new Random();
                vireoDispDist = arr[random.nextInt(arr.length)];
            }
        }
    }


}
