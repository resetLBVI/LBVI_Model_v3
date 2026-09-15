package lbvi;

import lbvi.Cowbird.CowbirdAgent;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;

public class Nest implements Steppable {
    //Identification and personal information
    public int nestID = 0;
    public int nestPatchID;
    public int nestTerrID;
    public int nestFemaleID;
    public int nestMaxNVireoEggs; //clutch size in the nest
    public double nestCoordX;
    public double nestCoordY;
    // The nest's developmental status is NOT stored: it is derived from the owning female's reproductive
    // stage via getStatus(). The one nest-owned lifecycle fact is whether it has ended (below).
    private boolean ended;
    //Nest state variables
    int nestDaysToCompletion; //why not change to nestCountdownToCompletion? indicate how many days females can lay eggs in the nest
    int nestCountdownToHatch;
    int nestCountdownToFledging;
    int nestCountdownToIndependence;
    int nestCountdownToRenesting;
    //cowbird related
    public boolean cowbirdArrived;
    // A Vireo egg loss during egg-laying (predation or cowbird, ODD 3.16) is stamped here so the female's
    // NEXT-day actEggLaying can make the desertion draw (ODD 3.10). -1 / null = no pending loss.
    public int vireoEggLossDay;
    public String vireoEggLossCause;

    Stoppable event; //allow the removal of a nest from the shcedule

    public Nest(LBVIEnvironment state, int nestID, int nestPatchID, int nestTerrID, int nestFemaleID) {
        this.nestID = nestID;
        this.nestPatchID = nestPatchID;
        this.nestTerrID = nestTerrID;
        this.nestFemaleID = nestFemaleID;
        this.nestMaxNVireoEggs = state.random.nextInt(state.mpVireoClutchSize);
        this.nestCoordX = state.vegTerrInfo.get(nestTerrID).terrCoordX;
        this.nestCoordY = state.vegTerrInfo.get(nestTerrID).terrCoordY;
        this.ended = false;
        this.nestDaysToCompletion = state.mpVireoNestBuildingDuration; // days of building before egg-laying (ODD)
        this.cowbirdArrived = false;
        this.vireoEggLossDay = -1;
        this.vireoEggLossCause = null;
        //nest countdown clock
        this.nestCountdownToHatch = state.mpIncubationStageDuration;
        this.nestCountdownToFledging = state.mpNestlingStageDuration;
        this.nestCountdownToIndependence = state.mpFledglingStageDuration;
        this.nestCountdownToRenesting = state.mpRenestingIntervalDuration;
    }

    @Override
    public void step(SimState simState) {
        LBVIEnvironment eState = (LBVIEnvironment) simState;
        // The nest's status is a derived view of the owning female's reproductive stage (see getStatus);
        // the female drives every transition (building, egg-laying, incubation, ...). The nest itself only
        // (a) stops once it has ended, and (b) runs the daily Cowbird-arrival trigger while it holds Vireo
        // eggs (ODD 3.15); post-arrival behavior is owned by the CowbirdAgent (ODD 3.16-3.19).
        // (TLB/defoliation mortality is disabled since 2026-08-01.)
        if (ended) {
            event.stop();
            return;
        }
        if (isEligibleForCowbirdArrival(eState)) {
            CowbirdAgent arrival = CowbirdAgent.tryArrival(eState, this);
            if (arrival != null) {
                this.cowbirdArrived = true;
            }
        }
    }

    // Nest building (the days-to-completion countdown and the daily false-start check) is driven by the
    // female in LBVIAgent.nestBuilding while she is in Stage.NESTBUILDING; the Nest only holds the
    // nestDaysToCompletion counter that she decrements.

    /* ============================================================================
                    NESTStatus: EGG LAYING
        Egg-laying now lives on the female LBVIAgent (LBVIAgent.eggLaying), which owns the egg
        count and reproductive stage. Egg predation (LBVIAgent.eggPredation) moved there too. The
        parasitism helpers below remain here and are called by the agent through its currentNest
        reference.
      ============================================================================== */

    /**
     * ODD 3.15 arrival eligibility: a Cowbird may arrive at this nest only if it holds one or more Vireo
     * eggs and no Cowbird has already been drawn to it this season (one arrival per nest).
     */
    public boolean isEligibleForCowbirdArrival(LBVIEnvironment state) {
        return !this.cowbirdArrived && vireoEggCount(state) > 0;
    }

    /**
     * ODD 3.16 parasitism eligibility, used by a roaming CowbirdAgent's nest search: a nest is a valid
     * target whenever it holds one or more Vireo eggs. Independent of {@link #cowbirdArrived}, since a
     * Cowbird that arrived elsewhere can still lay here.
     */
    public boolean isEligibleForParasitism(LBVIEnvironment state) {
        return vireoEggCount(state) > 0;
    }

    /** Live Vireo-egg count for this nest, read from the owning female (nestFemaleID = female's vireoID). */
    private int vireoEggCount(LBVIEnvironment state) {
        LBVIAgent female = state.lbviAgentMap.get(this.nestFemaleID);
        return (female == null) ? 0 : female.vireoNumVireoEggs;
    }

    /**
     * The nest's status is a derived view of the owning female's reproductive stage (nestFemaleID =
     * female's vireoID) - the female drives all transitions. The only nest-owned fact is whether the nest
     * has ended (a death/desertion/false-start event, which outlives the female's stage as she moves on).
     */
    public NESTStatus getStatus(LBVIEnvironment state) {
        if (this.ended) {
            return NESTStatus.DEAD;
        }
        LBVIAgent female = state.lbviAgentMap.get(this.nestFemaleID);
        if (female == null) {
            return NESTStatus.DEAD;
        }
        switch (female.vireoReproStage) {
            case NESTBUILDING:
                return NESTStatus.BUILDING;
            case EGGLAYING:
            case INCUBATION:
                return NESTStatus.EGG;
            case NESTLING:
                return NESTStatus.NESTLING;
            case FLEDGLING:
                return NESTStatus.FLEDGLING;
            default:
                return NESTStatus.COMPLETE;
        }
    }

    /** True once this nest has ended (death, desertion, or a false start during building). */
    public boolean isEnded() {
        return this.ended;
    }

    /** Marks this nest ended. Callers are responsible for stopping its schedule event and any cleanup. */
    public void markEnded() {
        this.ended = true;
    }

    /**
     * ODD 3.16: a Cowbird lays one egg in this Vireo nest (vireoNumCowbirdEggs +1) and, on a separate
     * draw, may remove one Vireo egg. A Vireo-egg loss stamps the nest so the female makes her desertion
     * draw on the following day (3.10).
     * @param state the simulation environment
     */
    public void cowbirdEggLayingInVireoNests(LBVIEnvironment state) {
        state.nParasitism++;
        // egg counts live on the owning female agent (looked up by nestFemaleID = female's vireoID)
        LBVIAgent female = state.lbviAgentMap.get(this.nestFemaleID);
        if (female == null) {
            return;
        }
        // ODD 3.16: the Cowbird always lays exactly one egg (vireoNumCowbirdEggs always +1) ...
        female.vireoNumCowbirdEggs++;
        // ... and a separate draw decides whether it also removes one of the female's Vireo eggs.
        if (state.random.nextBoolean(state.mpCowbirdVireoEggRemovalProb)) {
            if (female.vireoNumVireoEggs > 0) {
                female.vireoNumVireoEggs--;
            }
            // ODD 3.16 -> 3.10: a Vireo-egg loss triggers the female's desertion draw on the FOLLOWING day.
            this.vireoEggLossDay = state.currentJulianDay;
            this.vireoEggLossCause = "cowbird";
        }
    }

    /*
    ****************************************************************************************
    *                           Nest Mortality
    * ***************************************************************************************
     */
    public void checkVireoMortalityDueToTLB_EggNestling (LBVIEnvironment state) {
        if (state.vegTerrInfo.get(this.nestTerrID).terrDefoliated == true) {
            this.nestDeath(state, "defoliation");
        } else if (state.vegTerrInfo.get(this.nestTerrID).terrTamariskMortalityByTLB) {
            this.nestDeath(state, "TLB Mortality");
        }
    }

    public void checkVireoMortalityDueToTLB_Fledgling (LBVIEnvironment state) {
        int chickAged = state.mpFledglingStageDuration - this.nestCountdownToIndependence;
        if (state.vegTerrInfo.get(this.nestTerrID).terrDefoliated == true && chickAged < state.mpNDaysChickThermoregulate) {
            this.nestDeath(state, "defoliation");
        } else if (state.vegTerrInfo.get(this.nestTerrID).terrTamariskMortalityByTLB && chickAged < state.mpNDaysChickThermoregulate) {
            this.nestDeath(state, "TLB Mortality");
        }
    }

    /**
     * Returns the number of young currently in the nest, read from the owning
     * female agent (looked up by nestFemaleID, which is the female's vireoID).
     * The relevant count depends on the nest's current developmental stage:
     * eggs when EGG, nestlings when NESTLING, fledglings when FLEDGLING.
     * Returns 0 if the female can no longer be found or the nest holds no young
     * (e.g. CREATION/DEAD).
     */
    public int getNumIndividualsInNest(LBVIEnvironment state) {
        LBVIAgent female = state.lbviAgentMap.get(this.nestFemaleID);
        if (female == null) {
            return 0;
        }
        switch (getStatus(state)) {
            case EGG:
                return female.vireoNumVireoEggs;
            case NESTLING:
                return female.vireoNumVireoNestlings;
            case FLEDGLING:
                return female.vireoNumVireoFledglings;
            default:
                return 0;
        }
    }

    /**
     * Returns the species code for the young currently in this nest, derived live from the owning
     * female's stage-appropriate counts (nestFemaleID = female's vireoID). Under the revised rules a
     * nest may hold Vireo and Cowbird young at the same time without triggering nestDeath(), so this
     * reflects whatever is actually present rather than a single fixed value:
     *   "LBVI"      - only Vireo young present
     *   "BHCO"      - only Cowbird young present
     *   "LBVI+BHCO" - both present
     *   "none"      - the nest holds no young (e.g. BUILDING/DEAD), or the female is gone
     * The relevant pair of counts depends on nestStatus: eggs when EGG, nestlings when NESTLING,
     * fledglings when FLEDGLING.
     */
    public String getYoungSpecies(LBVIEnvironment state) {
        LBVIAgent female = state.lbviAgentMap.get(this.nestFemaleID);
        if (female == null) {
            return "none";
        }
        int nVireo;
        int nCowbird;
        switch (getStatus(state)) {
            case EGG:
                nVireo = female.vireoNumVireoEggs;
                nCowbird = female.vireoNumCowbirdEggs;
                break;
            case NESTLING:
                nVireo = female.vireoNumVireoNestlings;
                nCowbird = female.vireoNumCowbirdNestlings;
                break;
            case FLEDGLING:
                nVireo = female.vireoNumVireoFledglings;
                nCowbird = female.vireoNumCowbirdFledglings;
                break;
            default:
                return "none";
        }
        if (nVireo > 0 && nCowbird > 0) {
            return "LBVI+BHCO";
        } else if (nVireo > 0) {
            return "LBVI";
        } else if (nCowbird > 0) {
            return "BHCO";
        } else {
            return "none";
        }
    }

    public void nestDeath (LBVIEnvironment state, String eventType) {
        //1. the newborn will die (eggs, nestlings, or fledglings) -
        // I don't know which stage the agent will be created, but if the agent is already created, it should die (TODO! 2026-06-01)
        //2. record the mortality of nest - "step", "Date", "EventType", "TerrID", "VireoID", "NestID", "CowbirdID", "NumIndividual", "EntityType", "YoungSp"
        //   read counts/species while the nest is still in its live stage (before it is set to DEAD below)
        int NumIndividualDead = getNumIndividualsInNest(state);
        String youngSpecies = getYoungSpecies(state);
        NESTStatus statusAtDeath = getStatus(state); // capture the live status before marking the nest ended
        String nestDeath = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", state.schedule.getSteps(), state.currentJulianDay, eventType, this.nestTerrID,
                this.nestFemaleID, this.nestID, "NA", NumIndividualDead, statusAtDeath, youngSpecies);
        state.logMortalityWriter.addToFile(nestDeath);
        this.ended = true;
        state.activeNests.remove(this);
        event.stop();
        state.nestMap.remove(this.nestID);

    }

}
