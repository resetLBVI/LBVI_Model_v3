package lbvi;

import lbvi.Groundwater.GWInfoIdentifier;
import lbvi.Groundwater.WaterStress;
import lbvi.Traps.TrapInfoIndentifier;
import lbvi.Utils.OutputWriter;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.style.Style;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.SLD;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.field.grid.ObjectGrid2D;
import sim.field.grid.SparseGrid2D;
import sim.util.Int2D;
import java.awt.*;
import org.locationtech.jts.geom.Geometry;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LBVIEnvironment extends SimState {
    //setup input and output files path
    public String shpFilePath = "RESET_LBVI_inputData/RESET_merge_final.shp"; //* for background image use
    public String vegAttributePath = "RESET_LBVI_inputData/inTerritories2020.csv"; //*
    public String groundwaterFilePath = "RESET_LBVI_inputData/inGroundwaterBAU.csv"; //*
    public String trapPlanFilePath = "RESET_LBVI_inputData/inCowbirdTrapsRecent2020.csv"; //*
    public String initVireoPopulationFilePath = "RESET_LBVI_inputData/inVireoPopulation2020.csv"; //*
    // Annual PSHB beetle-impact scenario file (ODD 3.1/3.22). Used only when mpBeetleImpact is true. Default is
    // the Pendleton scenario; switch scenarios by pointing this at _SantaClaraCentral.csv / _SantaClaraEast.csv.
    public String beetleImpactFilePath = "RESET_LBVI_inputData/inBeetleImpact_Pendleton.csv"; //*
    public String tlbFilePath = "RESET_LBVI_inputData/";
    public String pshbFilePath = "RESET_LBVI_inputData/";
    //setup output files path
    public String logDebugFile = "logDebug.csv"; //*
    public boolean debugLog = true; //* master switch for per-agent debug logging (logDebug.csv); set false to disable
    public boolean debugLogOnChange = false; //* false = write a debug row every active step (full); true = write a row only when an agent's state changes (compact)
    public String logMortalityEventsFile = "logMortalityEvents.csv"; //*
    public String logSuccessEventsFile = "logSuccessEvents.csv"; //*
    public String logReproductivePerformanceFile = "logReproductivePerformance.csv"; //*
    public String logDispersalDistanceFile = "logDispersalDistance.csv"; //*
    public String logCowbirdArrivalOrDepartureFile = "logCowbirdArrivalOrDeparture.csv"; //*
    public String logDispersalOutcomeFile = "logDispersalOutcome.csv"; // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
    public String logBeetleImpactFile = "logBeetleImpact.csv"; // TEMP DIAGNOSTIC (remove after beetle-impact verification; see note temp-beetle-impact-logging)
    public String paramListFilePath = "paramList.csv"; //* run parameter snapshot (reproducibility)
    // OPTIONAL input. Path to a parameter CSV in the SAME "parameter,value" format that paramListFilePath
    // writes out. Blank/null = use the default (or Model-tab) values. When set, it is loaded at the very top
    // of start() and overrides the in-code defaults, so the team can run a configuration by editing a file
    // instead of retyping each value in the Model tab. Round-trips: run once, copy the emitted paramList.csv,
    // edit the values, point paramFilePath at your edited copy, and re-run.
    public String paramFilePath = "";
    public OutputWriter debugWriter; //this is a writer method class that helps us to wirte the logs
    public OutputWriter logMortalityWriter;
    public OutputWriter logSuccessWriter;
    public OutputWriter logReproductivePerformanceWriter;
    public OutputWriter logDispersalDistanceWriter;
    public OutputWriter logCowbirdArrivalOrDepartureWriter;
    public OutputWriter logDispersalOutcomeWriter; // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
    public OutputWriter beetleImpactWriter; // TEMP DIAGNOSTIC (remove after beetle-impact verification; see note temp-beetle-impact-logging); null unless mpBeetleImpact && debugLog
    //initialize grids to something sensible. For now, just placeholders; set sizes according to the world
    public final int worldWidth = 200; //* WIDTH and HEIGHT correspond to arbitrary display dimensions
    public final int worldHeight = 200; //* WIDTH and HEIGHT correspond to arbitrary display dimensions
    public SparseGrid2D vegetationGrid;
    // Background for UI (1×1 grid with a single image object)
    public ObjectGrid2D backgroundGrid;
    public BufferedImage backgroundImage;
    // World / shapefile bounds in map coordinates (same CRS as shapefile)
    public double shpMinX, shpMaxX, shpMinY, shpMaxY;
    // TerritoryID -> grid location (for placing agents)
    public Map<Integer, Int2D> territoryLocations = new HashMap<>();
    //initial vegetation map
    public Map<Integer, VegInfoIdentifier> vegTerrInfo = new HashMap<>();
    public Map<Integer, VegInfoIdentifier> vegPatchInfo = new HashMap<>();
    // geometry per terrID, populated by loadVegetationShapefile, consumed by loadVegAttributes
    Map<Integer, Geometry> terrGeometries = new HashMap<>();
    // all currently active Vireo nests; cowbirds search this list each day
    public List<Nest> activeNests = new ArrayList<>();
    // groundwater data keyed by patchID; refresh at the start of each simulation year
    // with that year's records by calling loadGroundwaterData() or filtering gwAllRecords
    public Map<Integer, GWInfoIdentifier> gwInfo = new HashMap<>();
    // trap plan: ALL rows (one per trap per year) loaded upfront as a flat list, filtered at runtime
    // by findOpenTraps() on calendar year + the open/close window. A List (not a Map keyed by trapID)
    // so the same TrapID repeated across years is retained rather than overwritten.
    public List<TrapInfoIndentifier> trapInfo = new ArrayList<>();
    // terrIDs of territories occupied at simulation start, loaded from initVireoPopulationFilePath
    public List<Integer> initVireoTerrIDs = new ArrayList<>();
    // Annual PSHB beetle impact (ODD 3.1): simulation year (1-based) -> terrIDs damaged that year. Built by
    // loadBeetleImpact from the selected runNum and offset-mapped hit.year; consumed by ActBeetleImpact each
    // year. Empty when beetleImpactFilePath is unset/absent (feature off).
    public Map<Integer, List<Integer>> beetleImpactByYear = new HashMap<>();
    //agent's state variables
    public int lbviAgentID = 0;
    public Map<Integer, LBVIAgent> lbviAgentMap = new HashMap<>(); // vireoID -> agent, for cross-agent lookups
    public Map<Integer, Nest> nestMap = new HashMap<>();
    // Globally-unique NestID source: minted when a nest's first egg is laid (LBVIAgent.eggLaying).
    // Never reset per-year, so every nest across all females AND all years gets a distinct id.
    private int nestIDCounter = 0;
    public int nextNestID() { return ++nestIDCounter; }

    //LBVI arrival model parameters
    public int lowerArrivalDate = 91; //* ODD: mpVireoFirstDate
    public int upperArrivalDate = 122; //* ODD: mpVireoLastDate
    public int modeArrivalDate = 107; //* ODD: mpVireoPeakDate
    //dispersal model parameters
    public double vireoBeyondKernalMAd = 20; //*
    public double vireoBeyondKernalFAd = 20; //*
    public double vireoBeyondKernalMJu = 20; //*
    public double vireoBeyondKernalFJu = 20; //*
    public double mpVireoTerrSearchDistanceM = 500; //* buffer distance in meters
    public double mpVireoSearchDistanceCoefficient = 0.1; //*
    public int mpVireoNumSearchAttempts = 20; //* number of expanding-radius search iterations an agent makes to build its potentialTerritoryList during dispersal (each iteration j widens the buffer by 1 + j*mpVireoSearchDistanceCoefficient); was hard-coded 3
    //* prob of dispersing much longer distances than the maximum distances shown in dispersal kernels.
    //One value per age/sex class (replaces the former single mpVireoLongDistanceDispersal).
    public double mpVireoLongDistanceDispersalFJu = 0.001;  //* female juvenile
    public double mpVireoLongDistanceDispersalFAd = 0.0196; //* female adult
    public double mpVireoLongDistanceDispersalMJu = 0.0491; //* male juvenile
    public double mpVireoLongDistanceDispersalMAd = 0.0165; //* male adult
    public double mpVireoLowerCutoffLDD = 20000; //*
    public double mpVireoUpperCutoffLDD = 200000; //*
    //simulation control
    public int mpInputSimulationDurationYears = 30; //* stop the simulation after this many years (1 year = 364 steps)
    //habitat selection model parameters
    public String mpHabitatQualityIndex = "terrQuality"; //* patchQuality or terrQuality
    public int mpTerrSelectionTrait = 3; //* 0: habitat quality 1: mate availability 2: habitat then mate availability 3: mate availability then habitat 4: completely random
    //reproduction model variables - nest model parameters
    public double mpDailyProbNestDesertionBeforeEggLaying = 0.0475; //* false start probability
    // Completed-clutch-size distribution (ODD 3.8), stored as a CUMULATIVE distribution (CDF): entry i is
    // P(clutch size <= i + 2), i.e. the "random number is less than" column of the §3.8 table. Index i maps
    // to a clutch of (i + 2) eggs, so the four values below correspond to clutch sizes 2, 3, 4, 5. The vector
    // must be non-decreasing with a final entry of ~1.0. Drawn in getNestMaxNEggs() to set each nest's clutch size.
    public double[] mpVireoPopClutchSize = {0.0233, 0.3805, 0.9962, 1.0000}; //* CDF for clutch sizes 2,3,4,5
    public int mpMaxNumAttempts = 5; //*
    public int mpVireoNestBuildingDuration = 5; //* a temporary default
    public int mpIncubationStageDuration = 14; //*
    public int mpNestlingStageDuration = 11; //*
    public int mpFledglingStageDuration = 25; //*
    public int mpRenestingIntervalDuration = 2; //*
    public int mpLastPossibleNestingDate = 169; //* the last possible nesting date is set at the end of September
    public double mpProbVireoIsFemale = 0.5; //* the probability of determining the newborn is a male or a female
    public int mpAgeUnderCowbirdRisk = 3; //vireo nest has eggs or chicks with an age in days less than this value can be parasite.
    //mortality model parameters
    public double mpDailyProbNestDesertionAfterEggLoss = 0.291; //* in egg-laying stage, determine if the parents will abandon the nest
    public double mpDailyMortalityProbEachEggEggLaying = 0.036; //* in egg-laying stage, determine the prob of each egg loss
    public double mpDailyMortalityProbEachEggIncubation = 0.029; //* ODD_v3 3.11: mortality for each egg during the incubation stage
    public double mpDailyMortalityProbEachNestling = 0.029; //* ODD_v3 3.12: mortality for each nestling during the nestling stage (baseline: 0 Cowbird nestlings)
    public double mpDailyMortalityProbEachFledgling = 0.02184; //* ODD_v3 3.13: for each of its dependent Vireo and Cowbird fledglings, against this parameter to determine the DMR

    public double mpDailyMortalityProbWholeNestIncubation = 0.036; //* ODDv3_3.11: mpDailyProbWholeNestMortalityPredatorDuringIncubation
    public double mpDailyMortalityProbWholeNestNestlingStage = 0.036; //* ODDv3_3.12: mpDailyProbWholeNestMortalityPredatorDuringNestling (baseline: 0 Cowbird nestlings)


    //--- Hypothesis test (non-default): Cowbird-nestling-dependent predation during the NESTLING stage ---
    //When true, the whole-nest and per-Vireo-nestling predation probabilities during the NESTLING stage
    //vary with the number of Cowbird nestlings sharing the nest (0 / 1 / 2+); the per-nestling elevation
    //applies to Vireo nestlings only — Cowbird nestlings always die at the baseline rate. When false
    //(default), the two baseline rates above are always used, reproducing the current behavior. The
    //"0 Cowbird nestlings" case reuses the baselines: mpDailyNestlingMortality (whole nest) and
    //mpDailyProbNestlingMortalityPredatorDuringNestling (per nestling). Resolved in
    //LBVIAgent.wholeNestMortalityDuringNestling() / nestlingMortalityDuringNestling().
    public boolean mpTestCowbirdNestlingMortalityHypothesis = false; //*
    public double mpDailyMortalityProbWholeNestNestlingStage1BHCONestling = 0.0396;      //*whole nest, exactly 1 Cowbird nestling
    public double mpDailyMortalityProbWholeNestNestlingStage2orMoreBHCONestlings = 0.04356; //*whole nest, 2+ Cowbird nestlings
    public double mpDailyMortalityProbEachNestling1BHCONestling = 0.0319;        //*per nestling, exactly 1 Cowbird nestling
    public double mpDailyMortalityProbEachNestling2orMoreBHCONestlings = 0.03509;  //*per nestling, 2+ Cowbird nestlings
    //--- Hypothesis test (non-default): Cowbird-fledgling-dependent predation during the FLEDGLING stage ---
    //Fledglings have left the nest, so there is no whole-nest mortality here — only per-fledgling draws.
    //When true, the per-Vireo-fledgling predation probability varies with the number of Cowbird fledglings
    //sharing the brood (0 / 1 / 2+); Cowbird-fledgling draws always use the baseline. When false (default),
    //the baseline mpDailyProbFledglingMortalityPredator is always used. The "0 Cowbird fledglings" case
    //reuses that baseline. Resolved in LBVIAgent.fledglingMortalityPredator().
    public boolean mpTestCowbirdFledglingMortalityHypothesis = false; //*
    public double mpDailyMortalityProbEachFledgling1BHCOFledgling = 0.024024;        //*per Vireo fledgling, exactly 1 Cowbird fledgling in brood
    public double mpDailyMortalityProbEachFledgling2orMoreBHCOFledglings = 0.026426; //*per Vireo fledgling, 2+ Cowbird fledglings in brood
    public int mpVireoPopMaxAgeF = 12; //*female maximum/death age (years); matches mpVireoStartPopFemaleAgeDist
    public int mpVireoPopMaxAgeM = 21; //*male maximum/death age (years); matches mpVireoStartPopMaleAgeDist
    // Startup age distributions from the Vireo Leslie matrix (survival estimates). Index i -> age (i+1),
    // so ages are 1-based. Weights need not sum to exactly 1 (they are normalized on use). Used only for the
    // initial (startup) population; agents born in-sim start at age 0.
    public double[] mpVireoStartPopFemaleAgeDist = {
            0.4649, 0.2488, 0.1331, 0.0713, 0.0381, 0.0204, 0.0109, 0.0059, 0.0031, 0.0017, 0.0009, 0.0005}; //12 weights
    public double[] mpVireoStartPopMaleAgeDist = {
            0.2805, 0.2018, 0.1453, 0.1045, 0.0752, 0.0541, 0.0390, 0.0280, 0.0202, 0.0145, 0.0104, 0.0075,
            0.0054, 0.0039, 0.0028, 0.0020, 0.0015, 0.0010, 0.0007, 0.0005, 0.0004}; //21 weights
    // Survival probabilities are drawn once per simulation run in start() from the distributions below
    // (parameter uncertainty). The defaults here are placeholders in case start() is bypassed.
    public double mpSurvivalProbJuvenileM = 0.5; // No need to show on the model table for users
    public double mpSurvivalProbJuvenileF = 0.5; // No need to show on the model table for users
    public double mpSurvivalProbAdultM = 0.9; // No need to show on the model table for users
    public double mpSurvivalProbAdultF = 0.9; // No need to show on the model table for users
    // Hyperparameters for the per-run survival-probability draws.
    // Gamma is parameterised as (shape, rate); Commons Math takes scale = 1/rate.  Beta is (alpha, beta).
    public double mpSurvivalProbJuvenileM_gammaShape = 156.45,  mpSurvivalProbJuvenileM_gammaRate = 267.30; //*
    public double mpSurvivalProbJuvenileF_gammaShape = 631.32,  mpSurvivalProbJuvenileF_gammaRate = 1467.25; //*
    public double mpSurvivalProbAdultM_betaAlpha = 132.94,      mpSurvivalProbAdultM_betaBeta = 34.55; //*
    public double mpSurvivalProbAdultF_gammaShape = 306.33,     mpSurvivalProbAdultF_gammaRate = 514.69; //*
    public int mpNDaysChickThermoregulate = 5; //eggs and young chicks cannot survive sun exposure in the absence of tamarisk foliage, the young will die
    //cowbird state variable
    public int cowbirdID;
    //Cowbird parasitism
    public int mpNCowbirdEggsPerSeason = 30; //*
    public double mpMaxCowbirdArrivalProb = 0.046738; //*
    public double mpMinCowbirdArrivalProb = 0.0004; //*
    public int mpCowbirdFirstDate = 91; //*Julian date
    public int mpPeakCowbirdDate = 105; //*Julian date
    public int mpCowbirdLastDate = 154; //*Julian date
    public double mpCowbirdNextNestIsVireo = 0.4; //* value updated on Sept 7, 2026
    public double mpCowbirdNestSearchDistanceM = 1000; //* value updated on Sep7, 2026
    public double mpCowbirdVireoEggRemovalProb = 0.588; //* the prob that the vireo's egg removal and Cowbird egg ++
    //Cowbird capture
    public double mpMaxCowbirdCaptureDistM = 4500; //* cowbird trap distance in meter
    public double mpCowbirdCaptureP90DistKM = 0.9; //* distance (km) at which the distance-based capture probability = 0.90 (near trap, high prob)
    public double mpCowbirdCaptureP10DistKM = 3.0; //* distance (km) at which the distance-based capture probability = 0.10 (far from trap, low prob)
    public int mpOpenDate  = 91;  //* Julian day traps open  (applies to all traps)
    public int mpCloseDate = 169; //* Julian day traps close (applies to all traps)
    public int mpStartCalendarYear = 2025; //* calendar year that simulation year 1 maps to; matches trap-plan Year column
    //beetle impact (ODD 3.1/3.22)
    // Master on/off switch for the PSHB beetle-impact simulation. When false (default), no beetle file is
    // loaded and no damage/recovery is applied, so runs behave exactly as before this feature. When true, the
    // scenario in beetleImpactFilePath is loaded and applied. mp-prefixed so it round-trips in the param CSV.
    public boolean mpBeetleImpact = true; //*
    public int mpBeetleDamageCounter = 4; //* seasons a damaged territory stays at quality 0 before recovering
    // Which PSHB replicate (runNum column) in the beetle-impact file to apply. The files hold 100 stochastic
    // replicates; a batch of 100 LBVI runs sets this 1..100 (one replicate per run). Decoupled from the run
    // seed on purpose, so the beetle scenario and LBVI stochasticity vary independently and reproducibly.
    public int mpBeetleRunNum = 1; //*
    // Beetle-file timeline anchor: the hit.year value that maps to LBVI simulation year 1. A row with
    // hit.year Y applies in simulation year (Y - mpBeetleStartYear + 1). Default 1985 = the PSHB files' first year.
    public int mpBeetleStartYear = 1985; //*
    //population summary data
    public int populationSize = 0;
    public int nPairs = 0; //|NPairs|- the number of unique female Vireos that created least one Nest.
    public int nSingles = 0; //|NSingles|- the number of unique Vireos that never had a Mate.
    public int nNests = 0; //|NNests| - the total number of unique nests created.
    public int nEggs = 0; //|NEggs| - the total number of eggs laid.
    public int nNestlings = 0; //|NNestlings| - the total number of eggs that hatched.
    public int nFledglings = 0; //|NFledgling| - the total number of nestlings that survived to fledging.
    public int nFledIndipendence = 0; //|NFledIndep| - the total number of fledglings that survived to independence.
    public int nSuccNests = 0; //|NSuccNests| - the total number of nests that produced at least one fledgling.
    public int nCowbirdArrival = 0; //|NCbrdArriv|- the total number of cowbirds that arrived in the model
    public int nParasitism = 0; //|NParasit| - the total number of cowbird parasitism events.
    public int nCowbirdCaptured = 0; //|NCowbrdCap| - the total number of cowbirds captured in cowbird traps prior to parasitizing a Vireo Nest.
    //Scheduling
    public int currentYear = 0;
    public int currentJulianDay = 0;



    public LBVIEnvironment(long seed) {
        super(seed);
        vegetationGrid = new SparseGrid2D(worldWidth, worldHeight);
        System.out.println("=+++++++=+++++++=+++++++=");
    }
    //end of Constructor

    /**
     * Headless / command-line entry point: run the model WITHOUT the GUI window, configured by a parameter
     * file. "Headless" = no Model tab, no map, no play button - it runs straight through and writes the output
     * CSVs, so a run can be launched from a terminal or a shell/R/Python script.
     *
     * Two equivalent argument styles are accepted:
     *   named (preferred, used by the sweep runner):
     *     java lbvi.LBVIEnvironment -paramFile run.csv -seed 42 -outputDir runs/run_0001_s42
     *     (-runId NAME is sugar for -outputDir runs/NAME; -outputDir wins if both are given)
     *   positional (back-compat):
     *     java lbvi.LBVIEnvironment [paramFile.csv] [seed]
     *
     *   paramFile.csv  optional - a "parameter,value" CSV (see loadParameterList); omit or "" to use the
     *                  in-code defaults. A file emitted as paramList.csv round-trips directly.
     *   seed           optional long; defaults to System.currentTimeMillis().
     *   outputDir      optional - a directory that EVERY output file is written into (created if needed), so
     *                  parallel sweep runs do not collide. Empty = write beside the jar as before. Output
     *                  paths are not loadable from the param CSV, so this flag is the way to separate runs.
     *
     * Run length comes from mpInputSimulationDurationYears (which the param file may itself override); the
     * LBVIObserver calls kill() at the final year, so the loop below ends on its own. Output CSVs are written
     * just as in a GUI run. (This is the single-config runner; it is NOT a parameter sweep - to sweep, call it
     * once per configuration from a script, each with its own paramFile, seed and outputDir.)
     */
    public static void main(String[] args) {
        String paramFile;
        long   seed;
        String outputDir;
        boolean named = args.length > 0 && args[0].startsWith("-");
        if (named) {
            paramFile = argValue(args, "-paramFile", "");
            String seedStr = argValue(args, "-seed", "");
            seed = seedStr.isEmpty() ? System.currentTimeMillis() : Long.parseLong(seedStr);
            outputDir = argValue(args, "-outputDir", "");
            String runId = argValue(args, "-runId", "");
            if (outputDir.isEmpty() && !runId.isEmpty()) outputDir = "runs/" + runId;
        } else {
            paramFile = (args.length >= 1) ? args[0] : "";
            seed      = (args.length >= 2) ? Long.parseLong(args[1]) : System.currentTimeMillis();
            outputDir = "";
        }

        LBVIEnvironment state = new LBVIEnvironment(seed);
        state.paramFilePath = paramFile;
        state.redirectOutputToDir(outputDir); // must run BEFORE start() creates the output writers
        state.start();

        System.out.println("Headless run started: seed=" + seed
                + ", paramFile=" + (paramFile.isEmpty() ? "(none; using defaults)" : paramFile)
                + ", outputDir=" + (outputDir.isEmpty() ? "(beside jar)" : outputDir)
                + ", durationYears=" + state.mpInputSimulationDurationYears);

        // The Observer's kill() clears the schedule at the final year, so schedule.step() then returns false.
        // The step cap is only a safety net in case no Observer is driving termination for a given config.
        long maxSteps = (long) state.mpInputSimulationDurationYears * 364L + 10L;
        while (state.schedule.getSteps() < maxSteps && state.schedule.step(state)) {
            // advance one day per iteration
        }
        state.finish();
        System.out.println("Headless run complete at step " + state.schedule.getSteps() + ".");
    }

    /** Return the value following {@code key} in args (e.g. "-seed" "42"), or {@code def} if absent. */
    private static String argValue(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) return args[i + 1];
        }
        return def;
    }

    /**
     * Prefix every output-file field with {@code dir} so a whole run's outputs land in one folder (created by
     * OutputWriter.getFileName when the files are opened). Called from the headless main BEFORE start(); a null
     * or blank dir leaves the defaults (write beside the jar). Keep this list in sync with the output files
     * created in start() - it must cover ALL of them so parallel sweep runs never collide.
     */
    public void redirectOutputToDir(String dir) {
        if (dir == null || dir.trim().isEmpty()) return;
        String d = dir.endsWith("/") ? dir : dir + "/";
        logDebugFile                     = d + logDebugFile;
        logMortalityEventsFile           = d + logMortalityEventsFile;
        logSuccessEventsFile             = d + logSuccessEventsFile;
        logReproductivePerformanceFile   = d + logReproductivePerformanceFile;
        logDispersalDistanceFile         = d + logDispersalDistanceFile;
        logCowbirdArrivalOrDepartureFile = d + logCowbirdArrivalOrDepartureFile;
        logDispersalOutcomeFile          = d + logDispersalOutcomeFile; // TEMP diagnostic
        logBeetleImpactFile              = d + logBeetleImpactFile;     // TEMP diagnostic
        paramListFilePath                = d + paramListFilePath;
    }


    public void start(){
        super.start();
        // OPTIONAL: override the in-code / Model-tab parameter values from a CSV before anything uses them.
        // Must happen here, ahead of the survival draws (which read the hyperparameters) and output setup.
        loadParameterList(this.paramFilePath);
        // Draw the four survival probabilities once for this run (parameter uncertainty).
        // Reproducibility: seed a Commons Math generator with MASON's run seed (this.seed()) so that
        // running the model with the same seed always yields the same four survival values. All four
        // distributions share this single generator and draw from it in sequence. Note this is a
        // separate stream from MASON's this.random (used for the per-individual survival checks);
        // both are seeded from the same run, so the whole run stays reproducible per seed.
        // Gamma: pass scale = 1/rate. Clamp to [0,1] since Gamma is unbounded above (Beta is already in [0,1]).
        // The RandomGenerator constructor form also requires the solver accuracy; we pass each
        // distribution's own DEFAULT_INVERSE_ABSOLUTE_ACCURACY, i.e. the library's normal default.
        org.apache.commons.math3.random.RandomGenerator rng =
                new org.apache.commons.math3.random.Well19937c(this.seed());
        this.mpSurvivalProbJuvenileM = Math.min(1.0, new GammaDistribution(
                rng, mpSurvivalProbJuvenileM_gammaShape, 1.0 / mpSurvivalProbJuvenileM_gammaRate,
                GammaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY).sample());
        this.mpSurvivalProbJuvenileF = Math.min(1.0, new GammaDistribution(
                rng, mpSurvivalProbJuvenileF_gammaShape, 1.0 / mpSurvivalProbJuvenileF_gammaRate,
                GammaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY).sample());
        this.mpSurvivalProbAdultM = new BetaDistribution(
                rng, mpSurvivalProbAdultM_betaAlpha, mpSurvivalProbAdultM_betaBeta,
                BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY).sample();
        this.mpSurvivalProbAdultF = Math.min(1.0, new GammaDistribution(
                rng, mpSurvivalProbAdultF_gammaShape, 1.0 / mpSurvivalProbAdultF_gammaRate,
                GammaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY).sample());
        System.out.println("Per-run survival probs: juvM=" + mpSurvivalProbJuvenileM + ", juvF=" + mpSurvivalProbJuvenileF
                + ", adultM=" + mpSurvivalProbAdultM + ", adultF=" + mpSurvivalProbAdultF);
        try {
            /*
            ################    OUTPUT  ###########################
             */
            //(output 1) create debug file
            String[] debugHeader = {"currentStep", "Year", "Date", "vireoID", "sex", "ageClass", "LBVIStage", "arrivalDate", "currentLoc", "potentialTerrCount", "terrMaleID", "terrFemaleID", "mateID", "mateStatus"};
            logDebugFile = OutputWriter.getFileName(this.logDebugFile, false);
            this.debugWriter = new OutputWriter(logDebugFile);
            this.debugWriter.createFile(debugHeader);
            //(output 2) create logMortalityEvents
            String[] logMortalityHeader = {"step", "Year", "Date", "EventType", "TerrID", "VireoID", "NestID", "CowbirdID", "NumIndividual", "EntityType", "YoungSp"}; //currently collect 11 data
            logMortalityEventsFile = OutputWriter.getFileName(this.logMortalityEventsFile, false);
            this.logMortalityWriter = new OutputWriter(logMortalityEventsFile);
            this.logMortalityWriter.createFile(logMortalityHeader);
            //(output 3) create logSuccessEvents - annual summary for the nests
            String[] logSuccessHeader = {"currentStep", "Year", "Date", "EventType", "TerrID", "VireoID", "NestID", "NumIndividual", "EntityType", "YoungSp"}; //curently collect 10 data
            logSuccessEventsFile = OutputWriter.getFileName(this.logSuccessEventsFile, false);
            this.logSuccessWriter = new OutputWriter(logSuccessEventsFile);
            this.logSuccessWriter.createFile(logSuccessHeader);
            //(output 4) create logReproductivePerformance - it's a population level summary for each year
            //13 attributes in logReproductivePerformance output
            String[] logReproductivePerformanceHeader = {"year", "populationSize", "NumPair", "NumSingles", "NumNests", "NumEggs", "NumNestlings", "NumFledglings", "NumIndependentFledglings", "NumSuccessfulNests", "NumCowbirdArrival", "NumParasitism", "NumCowbirdCaptured"};
            logReproductivePerformanceFile = OutputWriter.getFileName(this.logReproductivePerformanceFile, false);
            this.logReproductivePerformanceWriter = new OutputWriter(logReproductivePerformanceFile);
            this.logReproductivePerformanceWriter.createFile(logReproductivePerformanceHeader);
            //(output 5) create logDispersalDistance
            String[] logDispersalDistanceHeader = {"currentStep", "Year", "Date", "terrID", "vireoID", "PrevLoc", "Distance", "Sex", "AgeClass", "AgeYears"}; //determine the fields based on 2026-08-04 ODD; collect
            logDispersalDistanceFile = OutputWriter.getFileName(this.logDispersalDistanceFile, false);
            this.logDispersalDistanceWriter = new OutputWriter(logDispersalDistanceFile);
            this.logDispersalDistanceWriter.createFile(logDispersalDistanceHeader);
            //(output 6) create logCowbirdArrivalOrDeparture
            String[] logCowbirdArrivalOrDepartureHeader = {"currentStep", "Year", "date", "terrID", "CowbirdID", "ArrivalOrDepature"};
            logCowbirdArrivalOrDepartureFile = OutputWriter.getFileName(this.logCowbirdArrivalOrDepartureFile, false);
            this.logCowbirdArrivalOrDepartureWriter = new OutputWriter(logCowbirdArrivalOrDepartureFile);
            this.logCowbirdArrivalOrDepartureWriter.createFile(logCowbirdArrivalOrDepartureHeader);

            // TEMP DIAGNOSTIC (remove after non-breeding investigation; see note temp-dispersal-outcome-logging)
            String[] logDispersalOutcomeHeader = {"Year", "Date", "vireoID", "Sex", "AgeClass", "Outcome", "ListWasEmpty"};
            logDispersalOutcomeFile = OutputWriter.getFileName(this.logDispersalOutcomeFile, false);
            this.logDispersalOutcomeWriter = new OutputWriter(logDispersalOutcomeFile);
            this.logDispersalOutcomeWriter.createFile(logDispersalOutcomeHeader);
            // END TEMP DIAGNOSTIC

            // TEMP BEETLE DIAGNOSTIC (remove after beetle-impact verification; see note temp-beetle-impact-logging).
            // Traces each beetle-damaged territory from the step it is damaged through each season-end until it
            // recovers. Only created when beetle impacts AND debug logging are both on; otherwise beetleImpactWriter
            // stays null and logBeetleState(...) is a no-op.
            if (mpBeetleImpact && debugLog) {
                String[] logBeetleImpactHeader = {"currentStep", "Year", "Date", "terrID", "terrQuality",
                        "terrVegHealth", "terrBeetleDamage", "terrPreBeetleDamageQuality"};
                logBeetleImpactFile = OutputWriter.getFileName(this.logBeetleImpactFile, false);
                this.beetleImpactWriter = new OutputWriter(logBeetleImpactFile);
                this.beetleImpactWriter.createFile(logBeetleImpactHeader);
            }
            // END TEMP BEETLE DIAGNOSTIC

            //(output 7) write parameter snapshot for reproducibility
            paramListFilePath = OutputWriter.getFileName(this.paramListFilePath, false);
            writeParameterList(paramListFilePath);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        /*
        ####################    INPUT   ################################
         */
        //(input 1) load vegetation shapefile
        try {
            shpFilePath = OutputWriter.getFileName(this.shpFilePath, true);//get shpFile path
            loadVegetationShapefile(shpFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Fail to init vegetation shapefile metadata", e);
        }
        //(input 2) load vegetation attributes from CSV and build vegInfo
        try {
            vegAttributePath = OutputWriter.getFileName(this.vegAttributePath, true);
            loadVegAttributes(vegAttributePath);
        } catch (IOException e) {
            throw new RuntimeException("Fail to load vegetation attributes CSV", e);
        }
        //(input 3) load groundwater input (csv) → gwInfo
        try {
            groundwaterFilePath = OutputWriter.getFileName(this.groundwaterFilePath, true);
            loadGroundwaterData(groundwaterFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Fail to load groundwater input CSV", e);
        }
        //(input 4) load CowbirdTrappingPlan input (csv) → trapInfo
        try {
            trapPlanFilePath = OutputWriter.getFileName(this.trapPlanFilePath, true);
            loadTrapPlan(trapPlanFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Fail to load trap plan CSV", e);
        }

        //(input 5) load initial LBVI population file
        try {
            initVireoPopulationFilePath = OutputWriter.getFileName(this.initVireoPopulationFilePath, true);
            loadInitVireoPopulation(initVireoPopulationFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Fail to load initial Vireo population CSV", e);
        }
        //(input 6) load annual PSHB beetle-impact scenario (csv) → beetleImpactByYear (ODD 3.1/3.22), only when
        //the master switch mpBeetleImpact is on. Runs after the param file (loaded at the top of start()) so
        //mpBeetleImpact / mpBeetleRunNum / mpBeetleStartYear are already set.
        if (mpBeetleImpact) {
            try {
                if (this.beetleImpactFilePath != null && !this.beetleImpactFilePath.trim().isEmpty()) {
                    beetleImpactFilePath = OutputWriter.getFileName(this.beetleImpactFilePath, true);
                }
                loadBeetleImpact(beetleImpactFilePath);
            } catch (IOException e) {
                throw new RuntimeException("Fail to load beetle-impact CSV", e);
            }
        } else {
            System.out.println("Beetle impact OFF (mpBeetleImpact=false); no PSHB damage will be applied.");
        }
        //(10) initiate timer just to update the time
        LBVITimer systemTimer = new LBVITimer();
        // Run the clock EVERY step, at ordering -1 so it fires before the observer (0) and the agents (1)
        // and they all read a fresh currentYear / currentJulianDay this step. (Was scheduleOnce, which froze
        // the clock at day 0 / year 0 for the whole run.)
        schedule.scheduleRepeating(Schedule.EPOCH, -1, systemTimer);
        //(11) Make agents
        initAgents();
        //(11b) Log the initial (year 0) population state to logReproductivePerformance.csv. This captures the
        //input/starting condition before any simulation steps: every initial territory starts as a paired
        //male+female (see initAgents), so nPairs = number of initial territories and populationSize = all agents
        //on the grid. All breeding-outcome counters are 0 at year 0.
        int initNPairs = initVireoTerrIDs.size();
        long initPopSize = vegetationGrid.getAllObjects().numObjs;
        String year0Info = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                0, initPopSize, initNPairs, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); //13 attributes
        this.logReproductivePerformanceWriter.addToFile(year0Info);
        //(12) initiate observer
        LBVIObserver observer = new LBVIObserver();
        schedule.scheduleRepeating(observer);
        //(13) annual PSHB beetle impact (ODD 3.1), applied on day 0 of each year before breeding, only when
        //mpBeetleImpact is on. Ordering 0 (after the clock at -1 so currentYear is fresh; before agents at +1).
        //Recovery (ODD 3.22) runs from LBVIObserver at season end and is a no-op while beetleImpactByYear is empty.
        if (mpBeetleImpact) {
            schedule.scheduleRepeating(Schedule.EPOCH, 0, new lbvi.Beetles.ActBeetleImpact());
        }
        System.out.println("------------------END of the Start Step----------------------------");
    }

    public void loadVegetationShapefile(String shpPath) throws IOException {
        File file = new File(shpPath);
        Map<String, Object> params = new HashMap<>();
        params.put("url", file.toURI().toURL());

        DataStore store = DataStoreFinder.getDataStore(params);
        if (store == null) {
            throw new IOException("Could not open shapefile datastore: " + shpPath);
        }

        try {
            String typeName = store.getTypeNames()[0];
            SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
            SimpleFeatureCollection collection = featureSource.getFeatures();
            System.out.println("Reading vegetation shapefile (geometry only)...");
            System.out.println("Feature count (approx): " + collection.size());

            // get bounds for coordinate conversion
            ReferencedEnvelope env = featureSource.getBounds();
            if (env == null || env.isEmpty()) {
                throw new IOException("Shapefile has no bounds / envelope");
            }
            this.shpMinX = env.getMinX();
            this.shpMaxX = env.getMaxX();
            this.shpMinY = env.getMinY();
            this.shpMaxY = env.getMaxY();
            System.out.println("Bounds: X=[" + shpMinX + "," + shpMaxX + "] Y=[" + shpMinY + "," + shpMaxY + "]");

            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    SimpleFeature f = it.next();
                    Number terrIdAttr = (Number) f.getAttribute("TerrID");
                    int terrID = (terrIdAttr == null) ? 0 : Math.toIntExact(terrIdAttr.longValue());

                    // store polygon geometry keyed by terrID for use in loadVegAttributes()
                    Geometry geom = (Geometry) f.getDefaultGeometry();
                    terrGeometries.put(terrID, geom);

                    // NOTE: the shapefile is now used for UI display only (background image + world bounds).
                    // Agent grid placement is derived from the vegetation CSV's POINT_X/POINT_Y in
                    // loadVegAttributes(), so territoryLocations is NOT populated here. This decouples the
                    // model from the shapefile: a territory exists in the model as long as it is in the
                    // vegetation CSV, regardless of whether the shapefile contains its polygon.
                }
            }
            System.out.println("Loaded shapefile geometries: " + terrGeometries.size());
            buildBackgroundImageFromShapefile(featureSource);
        } finally {
            store.dispose();
        }
    }

    /**
     * Reads vegetation attributes from a CSV file and builds vegInfo.
     * Must be called after loadVegetationShapefile() so that terrGeometries
     * and territoryLocations are already populated.
     *
     * Expected CSV columns (with header row), looked up by name so column order
     * does not matter:
     *   PatchID, TerrID, POINT_X, POINT_Y, Quality, MapCode, MapUnit, VegName,
     *   pTrWillow, pShWillowM, pArundo, pTamarisk, Acres, VegHealth
     *
     * @param csvPath path to the vegetation attribute CSV file (vegAttributePath)
     */
    public void loadVegAttributes(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = stripBom(br.readLine());
            if (headerLine == null) throw new IOException("Vegetation attribute CSV is empty: " + csvPath);
            // build column-name → index map from header
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim().replace("\"", ""), i);
            requireColumns(col, "Vegetation attribute CSV", csvPath,
                    "PatchID", "TerrID", "POINT_X", "POINT_Y", "Acres", "MapCode", "MapUnit", "VegName",
                    "pTrWillow", "pShWillowM", "pArundo", "pTamarisk", "Quality", "VegHealth");
            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",", -1);
                for (int i = 0; i < cols.length; i++) cols[i] = cols[i].trim().replace("\"", "");
                int       patchID    = Integer.parseInt(cols[col.get("PatchID")]);
                int       terrID     = Integer.parseInt(cols[col.get("TerrID")]);
                double    coordX     = Double.parseDouble(cols[col.get("POINT_X")]);
                double    coordY     = Double.parseDouble(cols[col.get("POINT_Y")]);
                double    acres      = Double.parseDouble(cols[col.get("Acres")]);
                int       mapCode    = Integer.parseInt(cols[col.get("MapCode")]);
                String    mapUnit    = cols[col.get("MapUnit")];
                String    vegName    = cols[col.get("VegName")];
                double    twDens     = Double.parseDouble(cols[col.get("pTrWillow")]);
                double    swmfDens   = Double.parseDouble(cols[col.get("pShWillowM")]);
                double    arundoDens = Double.parseDouble(cols[col.get("pArundo")]);
                double    tamarDens  = Double.parseDouble(cols[col.get("pTamarisk")]);
                double    Quality    = Double.parseDouble(cols[col.get("Quality")]);
                VegHealth vegHealth  = VegHealth.fromLabel(cols[col.get("VegHealth")]);

                // geometry is display-only and never read by model logic; may be null for territories
                // absent from the shapefile (that is fine, the model does not use it).
                Geometry geom = terrGeometries.get(terrID);
                // Grid placement comes from the vegetation CSV's POINT_X/POINT_Y (coordX/coordY), not the
                // shapefile, so every territory in this CSV gets a location regardless of the shapefile.
                Int2D gridLoc = new Int2D(worldXToGrid(coordX), worldYToGrid(coordY));
                territoryLocations.put(terrID, gridLoc);
                vegTerrInfo.put(terrID, new VegInfoIdentifier(
                        patchID, terrID, coordX, coordY, acres, mapCode, mapUnit, vegName,
                        twDens, swmfDens, arundoDens, tamarDens, Quality, vegHealth, geom, gridLoc));
                vegPatchInfo.put(patchID, new VegInfoIdentifier(
                        patchID, terrID, coordX, coordY, acres, mapCode, mapUnit, vegName,
                        twDens, swmfDens, arundoDens, tamarDens, Quality, vegHealth, geom, gridLoc));
                count++;
            }
            System.out.println("Loaded vegetation attributes: " + count);
        }
    }

    /**
     * Reads annual groundwater data from a CSV file and populates gwInfo.
     * gwInfo is keyed by patchID and holds the record for the current simulation year.
     * Call this method once per simulation year (passing the year's records) to refresh it.
     *
     * Expected CSV columns (with header row):
     *   PatchID, year, DGW, WaterYearType, WaterStress
     *   WaterStress is stored as an integer code (1=ABOVENORMAL, 2=NORMAL, 3=BELOWNORMAL, 4=SEVEREDROUGHT)
     *
     * @param csvPath path to the groundwater input CSV (groundwaterFilePath)
     */
    public void loadGroundwaterData(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = stripBom(br.readLine());
            if (headerLine == null) throw new IOException("Groundwater CSV is empty: " + csvPath);
            // build column-name → index map from header
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim(), i);
            requireColumns(col, "Groundwater CSV", csvPath,
                    "PatchID", "year", "DGW", "WaterYearType", "WaterStress");
            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] v = line.split(",", -1);
                for (int i = 0; i < v.length; i++) v[i] = v[i].trim().replace("\"", "");
                int    patchID       = Integer.parseInt(v[col.get("PatchID")]);
                int    year          = Integer.parseInt(v[col.get("year")]);
                double dgw           = Double.parseDouble(v[col.get("DGW")]);
                String waterYearType = v[col.get("WaterYearType")];
                WaterStress waterStress = WaterStress.fromLabel(v[col.get("WaterStress")]);
                gwInfo.put(patchID, new GWInfoIdentifier(patchID, year, dgw, waterYearType, waterStress));
                count++;
            }
            System.out.println("Loaded groundwater records: " + count);
        }
    }

    /**
     * Write a snapshot of every model parameter to a CSV (parameter,value), one row per parameter.
     * Reflection-based: any non-static field whose name starts with "mp" is captured automatically,
     * so newly added parameters appear with no extra maintenance. Exception: the four per-run drawn
     * survival probabilities (mpSurvivalProb{Juvenile,Adult}{M,F}) are outputs, not user inputs, and
     * are excluded (their hyperparameters are captured instead). Overwrites the file each run.
     */
    /**
     * Input data-file path fields that may be read from / written to the parameter CSV, in addition to the
     * mp* parameters and the three arrival-date fields. These are the five files start() actually loads, so a
     * colleague can point the model at their own copies from the parameter file instead of the Model tab.
     * A path may be relative (resolved against the run base directory, as the defaults are) or absolute
     * (used as-is; see OutputWriter.getFileName). The log OUTPUT file fields and paramListFilePath/paramFilePath
     * are intentionally excluded; tlbFilePath/pshbFilePath are declared but never read, so they are excluded too.
     */
    private static boolean isInputPathParam(String name) {
        return name.equals("shpFilePath")
                || name.equals("vegAttributePath")
                || name.equals("groundwaterFilePath")
                || name.equals("trapPlanFilePath")
                || name.equals("initVireoPopulationFilePath")
                || name.equals("beetleImpactFilePath");
    }

    public void writeParameterList(String csvPath) {
        java.lang.reflect.Field[] fields = LBVIEnvironment.class.getDeclaredFields();
        java.util.Arrays.sort(fields, java.util.Comparator.comparing(java.lang.reflect.Field::getName));
        try (java.io.BufferedWriter out = new java.io.BufferedWriter(new java.io.FileWriter(csvPath, false))) {
            out.write("parameter,value\n");
            for (java.lang.reflect.Field f : fields) {
                String name = f.getName();
                // The four survival PROBABILITIES are drawn per run in start() from the hyperparameters
                // below (which are captured); they are outputs, not user inputs, and are reconstructable
                // from the hyperparameters + run seed, so they are excluded from the parameter snapshot.
                boolean drawnSurvivalProb = name.equals("mpSurvivalProbJuvenileM")
                        || name.equals("mpSurvivalProbJuvenileF")
                        || name.equals("mpSurvivalProbAdultM")
                        || name.equals("mpSurvivalProbAdultF");
                boolean keep = (name.startsWith("mp")
                        || name.equals("lowerArrivalDate")
                        || name.equals("upperArrivalDate")
                        || name.equals("modeArrivalDate")
                        || isInputPathParam(name)
                        // paramFilePath: write-only, for the record of WHICH config file drove this run. It is
                        // deliberately NOT in isInputPathParam (and so not loadable) - see loadParameterList.
                        || name.equals("paramFilePath"))
                        && !drawnSurvivalProb;
                if (!keep || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object value;
                try { value = f.get(this); } catch (IllegalAccessException e) { value = "<unreadable>"; }
                out.write(name + "," + formatParamValue(value) + "\n");
            }
            System.out.println("Parameter list written: " + csvPath);
        } catch (IOException e) {
            System.out.println("Failed to write parameter list: " + e);
        }
    }

    private static String formatParamValue(Object value) {
        String s;
        if (value == null)                   s = "";
        else if (value instanceof double[])  s = java.util.Arrays.toString((double[]) value).replace(",", ";");
        else if (value instanceof int[])     s = java.util.Arrays.toString((int[]) value).replace(",", ";");
        else if (value instanceof Object[])  s = java.util.Arrays.toString((Object[]) value).replace(",", ";");
        else                                 s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    /**
     * Load model parameters from a CSV and override the in-code defaults. This is the symmetric inverse of
     * writeParameterList(): it reads the exact same "parameter,value" format (optionally with the
     * "parameter,value" header row) and sets each matching field by reflection, so a run can be configured by
     * editing a file instead of the Model tab. A file produced by writeParameterList() round-trips directly.
     *
     * Acceptance mirrors writeParameterList()'s keep-rule: a row is applied only if its field exists, is
     * non-static, and its name starts with "mp" or is one of lowerArrivalDate/upperArrivalDate/modeArrivalDate.
     * The four per-run DRAWN survival probabilities (mpSurvivalProb{Juvenile,Adult}{M,F}) are never set here —
     * they are outputs drawn in start() from their hyperparameters (which ARE loaded) and are excluded from the
     * snapshot anyway. Supported types: double, int, long, boolean, String, double[], int[].
     *
     * Robustness: a blank/null path is a no-op (defaults are kept). A missing file logs a warning and leaves
     * defaults in place rather than aborting. Unknown names, non-accepted fields, and unparseable values are
     * each skipped with a warning; everything else is applied. Must be called BEFORE the parameters are used —
     * i.e. at the top of start(), ahead of the survival draws and output setup.
     *
     * @param csvPath path to the parameter CSV (blank/null = keep defaults)
     */
    public void loadParameterList(String csvPath) {
        if (csvPath == null || csvPath.trim().isEmpty()) {
            return; // no file supplied: keep the in-code / Model-tab values
        }
        java.io.File file = new java.io.File(csvPath);
        if (!file.isFile()) {
            System.out.println("WARNING: paramFilePath not found, using default parameter values: " + csvPath);
            return;
        }
        int applied = 0, skipped = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (firstLine) line = stripBom(line); // a BOM sits on the first line; strip so the first name matches
                // Split on the FIRST comma only: a parameter name is always a plain identifier (no comma),
                // and the value may itself contain commas only inside quotes (handled by stripQuotes).
                int comma = line.indexOf(',');
                if (comma < 0) continue;
                String name = line.substring(0, comma).trim();
                String rawValue = stripQuotes(line.substring(comma + 1).trim());
                // Skip the header row emitted by writeParameterList().
                if (firstLine && name.equals("parameter")) { firstLine = false; continue; }
                firstLine = false;

                // paramFilePath is WRITTEN into the snapshot (which config file this run used) but is NOT
                // loadable: this file is already open and being read, so setting it here would do nothing but
                // make the output snapshot misreport the file that was actually loaded. Skip it quietly (no
                // warning, not counted as "skipped") so loading an unedited snapshot stays clean.
                if (name.equals("paramFilePath")) continue;

                java.lang.reflect.Field f;
                try {
                    f = LBVIEnvironment.class.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    System.out.println("  paramFile: unknown parameter '" + name + "' - skipped");
                    skipped++;
                    continue;
                }
                boolean drawnSurvivalProb = name.equals("mpSurvivalProbJuvenileM")
                        || name.equals("mpSurvivalProbJuvenileF")
                        || name.equals("mpSurvivalProbAdultM")
                        || name.equals("mpSurvivalProbAdultF");
                boolean accept = (name.startsWith("mp")
                        || name.equals("lowerArrivalDate")
                        || name.equals("upperArrivalDate")
                        || name.equals("modeArrivalDate")
                        || isInputPathParam(name))
                        && !drawnSurvivalProb;
                if (!accept || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    System.out.println("  paramFile: '" + name + "' is not a loadable parameter - skipped");
                    skipped++;
                    continue;
                }
                try {
                    setFieldFromString(f, rawValue);
                    applied++;
                } catch (Exception e) {
                    System.out.println("  paramFile: could not set '" + name + "' = '" + rawValue + "' ("
                            + e.getMessage() + ") - skipped");
                    skipped++;
                }
            }
        } catch (IOException e) {
            System.out.println("WARNING: failed to read paramFilePath (" + csvPath + "): " + e
                    + " - using whatever was applied plus defaults");
        }
        System.out.println("Parameters loaded from " + csvPath + ": " + applied + " applied, " + skipped + " skipped.");
    }

    /** Remove one layer of CSV double-quoting (as written by formatParamValue), if present. */
    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }

    /**
     * Remove a leading UTF-8 BOM (U+FEFF) from a line if present, returning the line otherwise.
     * Excel and many Windows editors prepend a BOM when saving a CSV as UTF-8; it ends up as the
     * first character of the first line of the file. String.trim() does NOT strip U+FEFF, so without
     * this the BOM stays glued to the first field (a header name, or for the parameter file the first
     * parameter name), making that field impossible to match by name. Every CSV input loader runs its
     * first line through this so colleagues' hand-made CSVs load regardless of editor/encoding.
     * Null-safe: a null line (empty file) is returned unchanged.
     */
    private static String stripBom(String line) {
        return (line != null && !line.isEmpty() && line.charAt(0) == '﻿') ? line.substring(1) : line;
    }

    /**
     * Verify that the parsed header map contains every required column, and fail with a clear,
     * actionable message if not. Without this a missing or misspelled column surfaces only later as a
     * raw NullPointerException (col.get(name) returns null, then .intValue()/parse blows up) that points
     * at a line number instead of the real problem. Column matching is case-sensitive and exact, so the
     * message also lists what WAS found to make a typo/case mismatch obvious.
     *
     * @param col       header-name → index map built from the CSV header row
     * @param fileLabel human-readable name of the file for the message (e.g. "Vegetation attribute CSV")
     * @param csvPath   path to the file, included in the message
     * @param required  the column names the loader needs
     */
    private static void requireColumns(Map<String, Integer> col, String fileLabel, String csvPath,
                                       String... required) throws IOException {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String name : required) if (!col.containsKey(name)) missing.add(name);
        if (!missing.isEmpty()) {
            throw new IOException(fileLabel + " is missing required column(s): " + missing
                    + ". Columns found: " + col.keySet()
                    + ". Check the header row (names are case-sensitive and must match exactly). File: " + csvPath);
        }
    }

    /** Set a single field from its string form, dispatching on the field's declared type. */
    private void setFieldFromString(java.lang.reflect.Field f, String v) throws IllegalAccessException {
        f.setAccessible(true);
        Class<?> t = f.getType();
        if (t == double.class)        f.setDouble(this, Double.parseDouble(v));
        else if (t == int.class)      f.setInt(this, Integer.parseInt(v));
        else if (t == long.class)     f.setLong(this, Long.parseLong(v));
        else if (t == boolean.class)  f.setBoolean(this, Boolean.parseBoolean(v));
        else if (t == String.class)   f.set(this, v);
        else if (t == double[].class) f.set(this, parseDoubleArray(v));
        else if (t == int[].class)    f.set(this, parseIntArray(v));
        else throw new IllegalArgumentException("unsupported type " + t.getSimpleName());
    }

    /** Parse "[a; b; c]" (the formatParamValue array form) into a double[]. */
    private static double[] parseDoubleArray(String v) {
        String[] parts = arrayElements(v);
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }

    /** Parse "[a; b; c]" (the formatParamValue array form) into an int[]. */
    private static int[] parseIntArray(String v) {
        String[] parts = arrayElements(v);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    private static String[] arrayElements(String v) {
        String body = v.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]"))   body = body.substring(0, body.length() - 1);
        body = body.trim();
        if (body.isEmpty()) return new String[0];
        String[] parts = body.split(";");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    /**
     * Reads cowbird trap plan data from a CSV file and populates trapInfo (one entry per CSV row,
     * i.e. per trap per year).
     * All years are loaded upfront. Use logisticFunForCapture.findOpenTraps(state) at runtime
     * to filter to traps that are open on the current simulation day.
     *
     * Expected CSV columns (with header row):
     *   POINT_X, POINT_Y, TrapID, Year
     *   Open/close dates are model-wide parameters (mpOpenDate, mpCloseDate), not per-trap columns.
     *
     * @param csvPath path to the trap plan CSV (trapPlanFilePath)
     */
    public void loadTrapPlan(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = stripBom(br.readLine());
            if (headerLine == null) throw new IOException("Trap plan CSV is empty: " + csvPath);
            // build column-name → index map from header
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim(), i);
            requireColumns(col, "Trap plan CSV", csvPath, "TrapID", "POINT_X", "POINT_Y", "Year");
            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] v = line.split(",", -1);
                for (int i = 0; i < v.length; i++) v[i] = v[i].trim().replace("\"", "");
                int    trapID       = Integer.parseInt(v[col.get("TrapID")]);
                double xCoord       = Double.parseDouble(v[col.get("POINT_X")]);
                double yCoord       = Double.parseDouble(v[col.get("POINT_Y")]);
                int    year         = Integer.parseInt(v[col.get("Year")]);
                trapInfo.add(new TrapInfoIndentifier(xCoord, yCoord, trapID, year));
                count++;
            }
            System.out.println("Loaded trap records: " + count);
        }
    }

    /**
     * Reads the initial Vireo population CSV and populates initVireoTerrIDs. Each data row represents one
     * territory occupied at simulation start.
     *
     * Only the "TerrID" column is used; it is located by header name, so any other columns the file carries
     * (e.g. PatchID, POINT_X, POINT_Y, Q20) are ignored and the column order does not matter. A terrID that is
     * not present in the vegetation data is skipped with a warning. A UTF-8 BOM on the header line is tolerated.
     *
     * Expected CSV: a header row that includes a "TerrID" column (other columns optional).
     *
     * @param csvPath path to the initial Vireo population CSV (initVireoPopulationFilePath)
     */
    public void loadInitVireoPopulation(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = stripBom(br.readLine());
            if (headerLine == null) throw new IOException("Init Vireo population CSV is empty: " + csvPath);
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim(), i);
            requireColumns(col, "Init Vireo population CSV", csvPath, "TerrID");
            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] v = line.split(",", -1);
                for (int i = 0; i < v.length; i++) v[i] = v[i].trim().replace("\"", "");
                int terrID = Integer.parseInt(v[col.get("TerrID")]);
                if (!vegTerrInfo.containsKey(terrID)) {
                    System.err.println("loadInitVireoPopulation: terrID " + terrID + " not in vegetation data (inTerritories CSV), skipping");
                    continue;
                }
                initVireoTerrIDs.add(terrID);
                count++;
            }
            System.out.println("Loaded initial Vireo territories: " + count);
        }
    }

    /**
     * Reads the annual PSHB beetle-impact scenario CSV (a yearly time series from the PSHB model; ODD 6.4) and
     * populates {@link #beetleImpactByYear} (LBVI simulation year -> list of damaged TerrIDs). Consumed by
     * {@link lbvi.Beetles.ActBeetleImpact} each year (ODD 3.1).
     *
     * Expected CSV columns (header row; matched by name, order-independent; surrounding quotes tolerated):
     *   runNum, hit.year, hit.terr
     * Only rows whose runNum equals {@link #mpBeetleRunNum} are kept (the files hold 100 stochastic PSHB
     * replicates). Each kept row's hit.year is offset-mapped to an LBVI simulation year via
     * simYear = hit.year - mpBeetleStartYear + 1; rows that map before simulation year 1 are skipped. hit.terr
     * is a TerrID that must exist in the vegetation data (unknown terrIDs are warned and skipped).
     *
     * This input is OPTIONAL: an empty/blank path or a missing file leaves beetleImpactByYear empty (no beetle
     * impacts), so existing runs and input folders without the file are unaffected.
     *
     * @param csvPath path to the beetle-impact scenario CSV (beetleImpactFilePath)
     */
    public void loadBeetleImpact(String csvPath) throws IOException {
        if (csvPath == null || csvPath.trim().isEmpty()) {
            System.out.println("Beetle-impact file not set; no PSHB damage will be applied.");
            return;
        }
        java.io.File f = new java.io.File(csvPath);
        if (!f.isFile()) {
            System.out.println("Beetle-impact file not found (" + csvPath + "); no PSHB damage will be applied.");
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = stripBom(br.readLine());
            if (headerLine == null) throw new IOException("Beetle-impact CSV is empty: " + csvPath);
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim().replace("\"", ""), i);
            requireColumns(col, "Beetle-impact CSV", csvPath, "runNum", "hit.year", "hit.terr");
            int kept = 0, skippedBeforeStart = 0, skippedUnknownTerr = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] v = line.split(",", -1);
                for (int i = 0; i < v.length; i++) v[i] = v[i].trim().replace("\"", "");
                int runNum = Integer.parseInt(v[col.get("runNum")]);
                if (runNum != mpBeetleRunNum) continue; // keep only the selected PSHB replicate
                int hitYear = Integer.parseInt(v[col.get("hit.year")]);
                int terrID  = Integer.parseInt(v[col.get("hit.terr")]);
                int simYear = hitYear - mpBeetleStartYear + 1; // offset map onto the LBVI timeline (ODD: see 3.1)
                if (simYear < 1) { skippedBeforeStart++; continue; } // damage dated before the run starts
                if (!vegTerrInfo.containsKey(terrID)) {
                    System.err.println("loadBeetleImpact: terrID " + terrID + " (hit.year " + hitYear
                            + ") not in vegetation data (inTerritories CSV), skipping");
                    skippedUnknownTerr++;
                    continue;
                }
                beetleImpactByYear.computeIfAbsent(simYear, k -> new ArrayList<>()).add(terrID);
                kept++;
            }
            System.out.println("Loaded beetle-impact records: runNum=" + mpBeetleRunNum + ", " + kept
                    + " damage event(s) across " + beetleImpactByYear.size() + " simulation year(s)"
                    + " (skipped " + skippedBeforeStart + " before year 1, " + skippedUnknownTerr
                    + " unknown terrID).");
        }
    }

    /**
     * TEMP DIAGNOSTIC (remove after beetle-impact verification; see note temp-beetle-impact-logging).
     * Append one row to logBeetleImpact.csv capturing a territory's current beetle state. A no-op unless the
     * diagnostic writer was created (mpBeetleImpact && debugLog). Called from ActBeetleImpact when a territory
     * is damaged and at each season-end while it is still in its recovery countdown, so each damaged territory
     * is traced from damage through recovery.
     */
    public void logBeetleState(VegInfoIdentifier terr) {
        if (beetleImpactWriter == null) return; // diagnostic off
        beetleImpactWriter.addToFile(String.format("%d,%d,%d,%d,%d,%d,%d,%d",
                schedule.getSteps(), currentYear, currentJulianDay, terr.terrID,
                terr.terrQuality, terr.terrVegHealth, terr.terrBeetleDamage, terr.terrPreBeetleDamageQuality));
    }

    private void buildBackgroundImageFromShapefile(SimpleFeatureSource featureSource) {
        try {
            // 1. Create a MapContent and add your shapefile as a layer
            MapContent map = new MapContent();
            map.setTitle("LBVI Vegetation");

            // Custom polygon style (change colors as you like)
            Color outlineColor = Color.DARK_GRAY;
            Color fillColor    = new Color(0, 100, 0);      // dark green
            Style style        = SLD.createPolygonStyle(outlineColor, fillColor, 1.0f);

            FeatureLayer layer = new FeatureLayer(featureSource, style);
            map.addLayer(layer);
            // Use a simple default style (e.g., filled polygons with outline)
//            Style style = SLD.createSimpleStyle(featureSource.getSchema());
//            FeatureLayer layer = new FeatureLayer(featureSource, style);
//            map.addLayer(layer);

            // 2. Get the spatial bounds (envelope) of the data
            ReferencedEnvelope env = map.getViewport().getBounds();
            if (env == null || env.isEmpty()) {
                env = featureSource.getBounds();
            }
            if (env == null || env.isEmpty()) {
                System.err.println("buildBackgroundImageFromShapefile: no bounds for featureSource.");
                map.dispose();
                return;
            }

            // 3. The image size is tieing to our world grid size (e.g., use vegetationGrid)
             int imageWidth = vegetationGrid.getWidth();
             int imageHeight = vegetationGrid.getHeight();

            // 4. Create a BufferedImage and a Graphics2D to paint into
            BufferedImage image = new BufferedImage(
                    imageWidth, imageHeight,
                    BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D g = image.createGraphics();
            g.setPaint(Color.WHITE);
            // light blue background instead of white
//            Color lightBlue = new Color(220, 240, 255);
//            g.setPaint(lightBlue);
            g.fillRect(0, 0, imageWidth, imageHeight);

            // 5. Set up the renderer
            GTRenderer renderer = new StreamingRenderer();
            renderer.setMapContent(map);

            // 6. Render the map into the image
            Rectangle rect = new Rectangle(0, 0, imageWidth, imageHeight);
            renderer.paint(g, rect, env);

            g.dispose();
            map.dispose();

            // 7. Store in the environment so the UI can use it
            this.backgroundImage = image;

            // 8. Prepare the 1×1 background grid that MASON's ImagePortrayal2D uses
            if (this.backgroundGrid == null) {
                this.backgroundGrid = new ObjectGrid2D(1, 1);
            }
            this.backgroundGrid.set(0, 0, new Object());

            System.out.println("Background image created from shapefile: "
                    + imageWidth + "x" + imageHeight);

        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Failed to build background image from shapefile: " + ex.getMessage());
        }
    }
    /*
     *****************************************************************************************
     *                 Coordinate Conversion Helpers
     * ***************************************************************************************
     */
    private int worldXToGrid(double x) {
        double norm = (x - shpMinX) / (shpMaxX - shpMinX); // 0..1
        int col = (int) Math.floor(norm * vegetationGrid.getWidth());
        if (col < 0) col = 0;
        if (col >= vegetationGrid.getWidth()) col = vegetationGrid.getWidth() - 1;
        return col;
    }

    private int worldYToGrid(double y) {
        // Flip Y: shapefile maxY -> row 0, shapefile minY -> last row
        double norm = (shpMaxY - y) / (shpMaxY - shpMinY); // 0..1
        int row = (int) Math.floor(norm * vegetationGrid.getHeight());
        if (row < 0) row = 0;
        if (row >= vegetationGrid.getHeight()) row = vegetationGrid.getHeight() - 1;
        return row;
    }


    /*
    *****************************************************************************************
    *                           Scheduling
    * ***************************************************************************************
     */
    //update year
    public void updateYear() {this.currentYear = (int)(schedule.getSteps() / 364) + 1; } //1-based: steps 0-363 = year 1
    // Maps the 1-based simulation year to a calendar year (sim year 1 -> mpStartCalendarYear). Used to
    // match the trap plan's Year column. Not a getter (no "get" prefix) so it is not shown as a Model-tab property.
    public int currentCalendarYear() { return mpStartCalendarYear + (currentYear - 1); }
    public void updateJulianDay() {this.currentJulianDay = (int) (schedule.getSteps() % 364);}

    /*
     *********************************************************************************
     *                           MAKE AGENTS IN THE SPACE
     * ********************************************************************************
     */
    /*
    2025-11-19 For testing purpose, I randomly create 1000 agents in Santa Clara River area (terrID between 50378
    and 56334). Each location contains 5 females and 5 males (10 in total). So the agent can easily find a mate to start
    the breeding season. I create 100 locations for initiation.

     */
    public void initAgents() {
        //create initial males and females
        System.out.println("Init territories loaded: " + initVireoTerrIDs.size());
        for (int terrID: initVireoTerrIDs) {
            LBVIAgent female = makeAgent(true, true, terrID);
            female.event = schedule.scheduleRepeating(Schedule.EPOCH, 1, female);
            Int2D loc = territoryLocations.get(terrID);
            vegetationGrid.setObjectLocation(female, loc.x, loc.y);
            //The input pair keeps its territory and mate, but starts in ARRIVAL (not NESTBUILDING) so it runs the
            //arrival-date algorithm first. On arrival (checkArrival) it jumps straight to NESTBUILDING, skipping
            //DISPERSAL/NESTTERRSELECT/PAIR via the initialBreeder flag. This constrains the year-1 breeding season
            //to the spring arrival window like every other year; previously they started in NESTBUILDING on day 0
            //and bred the whole year, which inflated year-1 reproductive output.
            female.vireoMateStatus = true;
            female.vireoReproStage = Stage.ARRIVAL;
            female.initialBreeder = true;

            LBVIAgent male = makeAgent(false, true, terrID);
            male.event = schedule.scheduleRepeating(Schedule.EPOCH, 1, male);
            vegetationGrid.setObjectLocation(male, loc.x, loc.y);
            male.vireoMateStatus = true;
            male.vireoReproStage = Stage.ARRIVAL;
            male.initialBreeder = true;

            //mate with each other
            male.vireoMateID = female.vireoID;
            female.vireoMateID = male.vireoID;
        }
    }

    public LBVIAgent makeAgent(boolean vireoFemaleSex, boolean vireoAgeClassAdult, int vireoStartingLocation) {
        lbviAgentID ++;
        LBVIAgent a = new LBVIAgent(this, lbviAgentID, vireoFemaleSex, vireoAgeClassAdult,
                vireoStartingLocation, true);
        lbviAgentMap.put(lbviAgentID, a);
        return a;
    }

    /**
     * Draws a startup age (in years, 1-based) from the sex-specific Leslie-matrix age distribution
     * (mpVireoStartPopFemaleAgeDist / mpVireoStartPopMaleAgeDist), using cumulative "roulette-wheel"
     * sampling. Normalizes by the actual weight total, so the published probabilities do not need to sum to
     * exactly 1 (they sum to ~0.999 from rounding).
     */
    public int sampleStartupAge(boolean vireoFemaleSex) {
        double[] dist = vireoFemaleSex ? mpVireoStartPopFemaleAgeDist : mpVireoStartPopMaleAgeDist;
        double total = 0.0;
        for (double w : dist) total += w; //sum up all the probabilities
        double r = random.nextDouble() * total;   // r in [0, total)
        double cumulative = 0.0;
        for (int i = 0; i < dist.length; i++) {
            cumulative += dist[i];
            if (r < cumulative) return i + 1;      // index 0 -> age 1
        }
        return dist.length;                        // floating-point edge fallback: oldest age
    }

    /** Sex-specific maximum age (= death age), in years. */
    public int vireoMaxAge(boolean vireoFemaleSex) {
        return vireoFemaleSex ? mpVireoPopMaxAgeF : mpVireoPopMaxAgeM;
    }

    /*
    ==============================================================================================
                                Getters and Setters
    ===============================================================================================
     */

    // --- input / output file paths ---
    public String getShpFilePath() { return shpFilePath; }
    public void setShpFilePath(String val) { this.shpFilePath = val; }

    public String getVegAttributePath() { return vegAttributePath; }
    public void setVegAttributePath(String val) { this.vegAttributePath = val; }

    public String getGroundwaterFilePath() { return groundwaterFilePath; }
    public void setGroundwaterFilePath(String val) { this.groundwaterFilePath = val; }

    public String getTrapPlanFilePath() { return trapPlanFilePath; }
    public void setTrapPlanFilePath(String val) { this.trapPlanFilePath = val; }

    public String getInitVireoPopulationFilePath() { return initVireoPopulationFilePath; }
    public void setInitVireoPopulationFilePath(String val) { this.initVireoPopulationFilePath = val; }

    public String getBeetleImpactFilePath() { return beetleImpactFilePath; }
    public void setBeetleImpactFilePath(String val) { this.beetleImpactFilePath = val; }

    public String getLogDebugFile() { return logDebugFile; }
    public void setLogDebugFile(String val) { this.logDebugFile = val; }

    public boolean isDebugLog() { return debugLog; }
    public void setDebugLog(boolean val) { this.debugLog = val; }

    public boolean isDebugLogOnChange() { return debugLogOnChange; }
    public void setDebugLogOnChange(boolean val) { this.debugLogOnChange = val; }

    public String getLogMortalityEventsFile() { return logMortalityEventsFile; }
    public void setLogMortalityEventsFile(String val) { this.logMortalityEventsFile = val; }

    public String getLogSuccessEventsFile() { return logSuccessEventsFile; }
    public void setLogSuccessEventsFile(String val) { this.logSuccessEventsFile = val; }

    public String getLogReproductivePerformanceFile() { return logReproductivePerformanceFile; }
    public void setLogReproductivePerformanceFile(String val) { this.logReproductivePerformanceFile = val; }

    public String getLogDispersalDistanceFile() { return logDispersalDistanceFile; }
    public void setLogDispersalDistanceFile(String val) { this.logDispersalDistanceFile = val; }

    public String getLogCowbirdArrivalOrDepartureFile() { return logCowbirdArrivalOrDepartureFile; }
    public void setLogCowbirdArrivalOrDepartureFile(String val) { this.logCowbirdArrivalOrDepartureFile = val; }

    // --- optional parameter-file input (Model tab: leave blank to use the values shown here) ---
    public String getParamFilePath() { return paramFilePath; }
    public void setParamFilePath(String val) { this.paramFilePath = val; }

    // --- LBVI arrival model parameters ---
    public int getLowerArrivalDate() { return lowerArrivalDate; }
    public void setLowerArrivalDate(int val) { this.lowerArrivalDate = val; }

    public int getUpperArrivalDate() { return upperArrivalDate; }
    public void setUpperArrivalDate(int val) { this.upperArrivalDate = val; }

    public int getModeArrivalDate() { return modeArrivalDate; }
    public void setModeArrivalDate(int val) { this.modeArrivalDate = val; }

    // --- dispersal model parameters ---
    public double getVireoBeyondKernalMAd() { return vireoBeyondKernalMAd; }
    public void setVireoBeyondKernalMAd(double val) { this.vireoBeyondKernalMAd = val; }

    public double getVireoBeyondKernalFAd() { return vireoBeyondKernalFAd; }
    public void setVireoBeyondKernalFAd(double val) { this.vireoBeyondKernalFAd = val; }

    public double getVireoBeyondKernalMJu() { return vireoBeyondKernalMJu; }
    public void setVireoBeyondKernalMJu(double val) { this.vireoBeyondKernalMJu = val; }

    public double getVireoBeyondKernalFJu() { return vireoBeyondKernalFJu; }
    public void setVireoBeyondKernalFJu(double val) { this.vireoBeyondKernalFJu = val; }

    public double getMpVireoTerrSearchDistanceM() { return mpVireoTerrSearchDistanceM; }
    public void setMpVireoTerrSearchDistanceM(double val) { this.mpVireoTerrSearchDistanceM = val; }

    public double getMpVireoSearchDistanceCoefficient() { return mpVireoSearchDistanceCoefficient; }
    public void setMpVireoSearchDistanceCoefficient(double val) { this.mpVireoSearchDistanceCoefficient = val; }

    public int getMpVireoNumSearchAttempts() { return mpVireoNumSearchAttempts; }
    public void setMpVireoNumSearchAttempts(int val) { this.mpVireoNumSearchAttempts = val; }

    public double getMpVireoLongDistanceDispersalFJu() { return mpVireoLongDistanceDispersalFJu; }
    public void setMpVireoLongDistanceDispersalFJu(double val) { this.mpVireoLongDistanceDispersalFJu = val; }

    public double getMpVireoLongDistanceDispersalFAd() { return mpVireoLongDistanceDispersalFAd; }
    public void setMpVireoLongDistanceDispersalFAd(double val) { this.mpVireoLongDistanceDispersalFAd = val; }

    public double getMpVireoLongDistanceDispersalMJu() { return mpVireoLongDistanceDispersalMJu; }
    public void setMpVireoLongDistanceDispersalMJu(double val) { this.mpVireoLongDistanceDispersalMJu = val; }

    public double getMpVireoLongDistanceDispersalMAd() { return mpVireoLongDistanceDispersalMAd; }
    public void setMpVireoLongDistanceDispersalMAd(double val) { this.mpVireoLongDistanceDispersalMAd = val; }

    public double getMpVireoLowerCutoffLDD() { return mpVireoLowerCutoffLDD; }
    public void setMpVireoLowerCutoffLDD(double val) { this.mpVireoLowerCutoffLDD = val; }

    public double getMpVireoUpperCutoffLDD() { return mpVireoUpperCutoffLDD; }
    public void setMpVireoUpperCutoffLDD(double val) { this.mpVireoUpperCutoffLDD = val; }

    // --- simulation control ---
    public int getMpInputSimulationDurationYears() { return mpInputSimulationDurationYears; }
    public void setMpInputSimulationDurationYears(int val) { this.mpInputSimulationDurationYears = val; }

    // --- habitat selection model parameters ---
    public String getMpHabitatQualityIndex() { return mpHabitatQualityIndex; }
    public void setMpHabitatQualityIndex(String val) { this.mpHabitatQualityIndex = val; }

    public int getMpTerrSelectionTrait() { return mpTerrSelectionTrait; }
    public void setMpTerrSelectionTrait(int val) { this.mpTerrSelectionTrait = val; }
    // MASON domain: renders mpTerrSelectionTrait as a dropdown in the Model tab. The property stays an
    // int; the selected list index (0-4) is the stored value, so the labels map directly to the codes.
    public Object domMpTerrSelectionTrait() {
        return new String[] {
            "0: habitat quality",
            "1: mate availability",
            "2: habitat then mate availability",
            "3: mate availability then habitat",
            "4: completely random"
        };
    }

    // --- reproduction / nest model parameters ---
    public double getMpDailyProbNestDesertionBeforeEggLaying() { return mpDailyProbNestDesertionBeforeEggLaying; }
    public void setMpDailyProbNestDesertionBeforeEggLaying(double val) { this.mpDailyProbNestDesertionBeforeEggLaying = val; }

    public int getMpMaxNumAttempts() { return mpMaxNumAttempts; }
    public void setMpMaxNumAttempts(int val) { this.mpMaxNumAttempts = val; }

    public int getMpVireoNestBuildingDuration() { return mpVireoNestBuildingDuration; }
    public void setMpVireoNestBuildingDuration(int val) { this.mpVireoNestBuildingDuration = val; }

    public int getMpIncubationStageDuration() { return mpIncubationStageDuration; }
    public void setMpIncubationStageDuration(int val) { this.mpIncubationStageDuration = val; }

    public int getMpNestlingStageDuration() { return mpNestlingStageDuration; }
    public void setMpNestlingStageDuration(int val) { this.mpNestlingStageDuration = val; }

    public int getMpFledglingStageDuration() { return mpFledglingStageDuration; }
    public void setMpFledglingStageDuration(int val) { this.mpFledglingStageDuration = val; }

    public int getMpRenestingIntervalDuration() { return mpRenestingIntervalDuration; }
    public void setMpRenestingIntervalDuration(int val) { this.mpRenestingIntervalDuration = val; }

    public int getMpLastPossibleNestingDate() { return mpLastPossibleNestingDate; }
    public void setMpLastPossibleNestingDate(int val) { this.mpLastPossibleNestingDate = val; }

    public double getMpProbVireoIsFemale() { return mpProbVireoIsFemale; }
    public void setMpProbVireoIsFemale(double val) { this.mpProbVireoIsFemale = val; }

    // --- startup age distributions, exposed to the model tab as editable comma-separated TEXT ---
    // A raw double[] renders as "double[]@<hash>" in MASON and cannot be edited, so we expose a String
    // view instead: type the weights separated by commas, e.g. "0.4649, 0.2488, 0.1331". Spaces and
    // newlines between values are fine. Keep the NUMBER of values aligned with the matching max-age
    // field (F -> mpVireoPopMaxAgeF, M -> mpVireoPopMaxAgeM). Weights need not sum to 1 (normalized on use).
    public String getMpVireoStartPopFemaleAgeDist() { return doubleArrayToCsv(mpVireoStartPopFemaleAgeDist); }
    public void setMpVireoStartPopFemaleAgeDist(String csv) { this.mpVireoStartPopFemaleAgeDist = csvToDoubleArray(csv, mpVireoStartPopFemaleAgeDist); }

    public String getMpVireoStartPopMaleAgeDist() { return doubleArrayToCsv(mpVireoStartPopMaleAgeDist); }
    public void setMpVireoStartPopMaleAgeDist(String csv) { this.mpVireoStartPopMaleAgeDist = csvToDoubleArray(csv, mpVireoStartPopMaleAgeDist); }

    // Clutch-size distribution (ODD 3.8), a CUMULATIVE distribution (CDF). Exposed as CSV text like the age
    // distributions above; index i -> a clutch of (i + 2) eggs, e.g. "0.0233, 0.3805, 0.9962, 1.0" -> sizes
    // 2, 3, 4, 5. Values must be non-decreasing and end at ~1.0. A bad or empty edit leaves the vector as is.
    public String getMpVireoPopClutchSize() { return doubleArrayToCsv(mpVireoPopClutchSize); }
    public void setMpVireoPopClutchSize(String csv) { this.mpVireoPopClutchSize = csvToDoubleArray(csv, mpVireoPopClutchSize); }

    /** Formats a double[] as comma-separated text for display in the model tab. */
    private static String doubleArrayToCsv(double[] a) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(a[i]);
        }
        return sb.toString();
    }

    /** Parses comma/space/newline-separated text into a double[]. On empty or unparseable input it
     *  returns the current value unchanged, so a stray keystroke in the GUI cannot wipe the distribution. */
    private static double[] csvToDoubleArray(String csv, double[] current) {
        if (csv == null) return current;
        String[] tokens = csv.trim().split("[,\\s]+");
        java.util.ArrayList<Double> vals = new java.util.ArrayList<>();
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            try { vals.add(Double.parseDouble(t)); }
            catch (NumberFormatException e) { return current; } // reject the whole edit on any bad token
        }
        if (vals.isEmpty()) return current;
        double[] out = new double[vals.size()];
        for (int i = 0; i < out.length; i++) out[i] = vals.get(i);
        return out;
    }

    // --- mortality model parameters ---
    public double getMpDailyProbNestDesertionAfterEggLoss() { return mpDailyProbNestDesertionAfterEggLoss; }
    public void setMpDailyProbNestDesertionAfterEggLoss(double val) { this.mpDailyProbNestDesertionAfterEggLoss = val; }

    public double getMpDailyMortalityProbEachEggEggLaying() { return mpDailyMortalityProbEachEggEggLaying; }
    public void setMpDailyMortalityProbEachEggEggLaying(double val) { this.mpDailyMortalityProbEachEggEggLaying = val; }

    public double getMpDailyMortalityProbEachEggIncubation() { return mpDailyMortalityProbEachEggIncubation; }
    public void setMpDailyMortalityProbEachEggIncubation(double val) { this.mpDailyMortalityProbEachEggIncubation = val; }

    public double getMpDailyMortalityProbEachNestling() { return mpDailyMortalityProbEachNestling; }
    public void setMpDailyMortalityProbEachNestling(double val) { this.mpDailyMortalityProbEachNestling = val; }

    public double getMpDailyMortalityProbEachFledgling() { return mpDailyMortalityProbEachFledgling; }
    public void setMpDailyMortalityProbEachFledgling(double val) { this.mpDailyMortalityProbEachFledgling = val; }

    public double getMpDailyMortalityProbWholeNestIncubation() { return mpDailyMortalityProbWholeNestIncubation; }
    public void setMpDailyMortalityProbWholeNestIncubation(double val) { this.mpDailyMortalityProbWholeNestIncubation = val; }

    public double getMpDailyMortalityProbWholeNestNestlingStage() { return mpDailyMortalityProbWholeNestNestlingStage; }
    public void setMpDailyMortalityProbWholeNestNestlingStage(double val) { this.mpDailyMortalityProbWholeNestNestlingStage = val; }

    // --- hypothesis test: Cowbird-nestling-dependent predation (nestling stage) ---
    public boolean isMpTestCowbirdNestlingMortalityHypothesis() { return mpTestCowbirdNestlingMortalityHypothesis; }
    public void setMpTestCowbirdNestlingMortalityHypothesis(boolean val) { this.mpTestCowbirdNestlingMortalityHypothesis = val; }

    public double getMpDailyMortalityProbWholeNestNestlingStage1BHCONestling() { return mpDailyMortalityProbWholeNestNestlingStage1BHCONestling; }
    public void setMpDailyMortalityProbWholeNestNestlingStage1BHCONestling(double val) { this.mpDailyMortalityProbWholeNestNestlingStage1BHCONestling = val; }

    public double getMpDailyMortalityProbWholeNestNestlingStage2orMoreBHCONestlings() { return mpDailyMortalityProbWholeNestNestlingStage2orMoreBHCONestlings; }
    public void setMpDailyMortalityProbWholeNestNestlingStage2orMoreBHCONestlings(double val) { this.mpDailyMortalityProbWholeNestNestlingStage2orMoreBHCONestlings = val; }

    public double getMpDailyMortalityProbEachNestling1BHCONestling() { return mpDailyMortalityProbEachNestling1BHCONestling; }
    public void setMpDailyMortalityProbEachNestling1BHCONestling(double val) { this.mpDailyMortalityProbEachNestling1BHCONestling = val; }

    public double getMpDailyMortalityProbEachNestling2orMoreBHCONestlings() { return mpDailyMortalityProbEachNestling2orMoreBHCONestlings; }
    public void setMpDailyMortalityProbEachNestling2orMoreBHCONestlings(double val) { this.mpDailyMortalityProbEachNestling2orMoreBHCONestlings = val; }

    // --- hypothesis test: Cowbird-fledgling-dependent predation (fledgling stage) ---
    public boolean isMpTestCowbirdFledglingMortalityHypothesis() { return mpTestCowbirdFledglingMortalityHypothesis; }
    public void setMpTestCowbirdFledglingMortalityHypothesis(boolean val) { this.mpTestCowbirdFledglingMortalityHypothesis = val; }

    public double getMpDailyMortalityProbEachFledgling1BHCOFledgling() { return mpDailyMortalityProbEachFledgling1BHCOFledgling; }
    public void setMpDailyMortalityProbEachFledgling1BHCOFledgling(double val) { this.mpDailyMortalityProbEachFledgling1BHCOFledgling = val; }

    public double getMpDailyMortalityProbEachFledgling2orMoreBHCOFledglings() { return mpDailyMortalityProbEachFledgling2orMoreBHCOFledglings; }
    public void setMpDailyMortalityProbEachFledgling2orMoreBHCOFledglings(double val) { this.mpDailyMortalityProbEachFledgling2orMoreBHCOFledglings = val; }

    public int getMpVireoPopMaxAgeF() { return mpVireoPopMaxAgeF; }
    public void setMpVireoPopMaxAgeF(int val) { this.mpVireoPopMaxAgeF = val; }

    public int getMpVireoPopMaxAgeM() { return mpVireoPopMaxAgeM; }
    public void setMpVireoPopMaxAgeM(int val) { this.mpVireoPopMaxAgeM = val; }

//    // --- survival probabilities (drawn per run in start(); defaults are placeholders) ---
//    public double getMpSurvivalProbJuvenileM() { return mpSurvivalProbJuvenileM; }
//    public void setMpSurvivalProbJuvenileM(double val) { this.mpSurvivalProbJuvenileM = val; }
//
//    public double getMpSurvivalProbJuvenileF() { return mpSurvivalProbJuvenileF; }
//    public void setMpSurvivalProbJuvenileF(double val) { this.mpSurvivalProbJuvenileF = val; }
//
//    public double getMpSurvivalProbAdultM() { return mpSurvivalProbAdultM; }
//    public void setMpSurvivalProbAdultM(double val) { this.mpSurvivalProbAdultM = val; }
//
//    public double getMpSurvivalProbAdultF() { return mpSurvivalProbAdultF; }
//    public void setMpSurvivalProbAdultF(double val) { this.mpSurvivalProbAdultF = val; }

    // --- survival-probability draw hyperparameters ---
    public double getMpSurvivalProbJuvenileM_gammaShape() { return mpSurvivalProbJuvenileM_gammaShape; }
    public void setMpSurvivalProbJuvenileM_gammaShape(double val) { this.mpSurvivalProbJuvenileM_gammaShape = val; }

    public double getMpSurvivalProbJuvenileM_gammaRate() { return mpSurvivalProbJuvenileM_gammaRate; }
    public void setMpSurvivalProbJuvenileM_gammaRate(double val) { this.mpSurvivalProbJuvenileM_gammaRate = val; }

    public double getMpSurvivalProbJuvenileF_gammaShape() { return mpSurvivalProbJuvenileF_gammaShape; }
    public void setMpSurvivalProbJuvenileF_gammaShape(double val) { this.mpSurvivalProbJuvenileF_gammaShape = val; }

    public double getMpSurvivalProbJuvenileF_gammaRate() { return mpSurvivalProbJuvenileF_gammaRate; }
    public void setMpSurvivalProbJuvenileF_gammaRate(double val) { this.mpSurvivalProbJuvenileF_gammaRate = val; }

    public double getMpSurvivalProbAdultM_betaAlpha() { return mpSurvivalProbAdultM_betaAlpha; }
    public void setMpSurvivalProbAdultM_betaAlpha(double val) { this.mpSurvivalProbAdultM_betaAlpha = val; }

    public double getMpSurvivalProbAdultM_betaBeta() { return mpSurvivalProbAdultM_betaBeta; }
    public void setMpSurvivalProbAdultM_betaBeta(double val) { this.mpSurvivalProbAdultM_betaBeta = val; }

    public double getMpSurvivalProbAdultF_gammaShape() { return mpSurvivalProbAdultF_gammaShape; }
    public void setMpSurvivalProbAdultF_gammaShape(double val) { this.mpSurvivalProbAdultF_gammaShape = val; }

    public double getMpSurvivalProbAdultF_gammaRate() { return mpSurvivalProbAdultF_gammaRate; }
    public void setMpSurvivalProbAdultF_gammaRate(double val) { this.mpSurvivalProbAdultF_gammaRate = val; }

    // --- Cowbird parasitism ---
    public int getMpNCowbirdEggsPerSeason() { return mpNCowbirdEggsPerSeason; }
    public void setMpNCowbirdEggsPerSeason(int val) { this.mpNCowbirdEggsPerSeason = val; }

    public double getMpMaxCowbirdArrivalProb() { return mpMaxCowbirdArrivalProb; }
    public void setMpMaxCowbirdArrivalProb(double val) { this.mpMaxCowbirdArrivalProb = val; }

    public double getMpMinCowbirdArrivalProb() { return mpMinCowbirdArrivalProb; }
    public void setMpMinCowbirdArrivalProb(double val) { this.mpMinCowbirdArrivalProb = val; }

    public int getMpCowbirdFirstDate() { return mpCowbirdFirstDate; }
    public void setMpCowbirdFirstDate(int val) { this.mpCowbirdFirstDate = val; }

    public int getMpPeakCowbirdDate() { return mpPeakCowbirdDate; }
    public void setMpPeakCowbirdDate(int val) { this.mpPeakCowbirdDate = val; }

    public int getMpCowbirdLastDate() { return mpCowbirdLastDate; }
    public void setMpCowbirdLastDate(int val) { this.mpCowbirdLastDate = val; }

    public double getMpCowbirdNextNestIsVireo() { return mpCowbirdNextNestIsVireo; }
    public void setMpCowbirdNextNestIsVireo(double val) { this.mpCowbirdNextNestIsVireo = val; }

    public double getMpCowbirdNestSearchDistanceM() { return mpCowbirdNestSearchDistanceM; }
    public void setMpCowbirdNestSearchDistanceM(double val) { this.mpCowbirdNestSearchDistanceM = val; }

    public double getMpCowbirdVireoEggRemovalProb() { return mpCowbirdVireoEggRemovalProb; }
    public void setMpCowbirdVireoEggRemovalProb(double val) { this.mpCowbirdVireoEggRemovalProb = val; }

    // --- Cowbird capture ---
    public double getMpMaxCowbirdCaptureDistM() { return mpMaxCowbirdCaptureDistM; }
    public void setMpMaxCowbirdCaptureDistM(double val) { this.mpMaxCowbirdCaptureDistM = val; }

    public double getMpCowbirdCaptureP90DistKM() { return mpCowbirdCaptureP90DistKM; }
    public void setMpCowbirdCaptureP90DistKM(double val) { this.mpCowbirdCaptureP90DistKM = val; }

    public double getMpCowbirdCaptureP10DistKM() { return mpCowbirdCaptureP10DistKM; }
    public void setMpCowbirdCaptureP10DistKM(double val) { this.mpCowbirdCaptureP10DistKM = val; }

    public int getMpStartCalendarYear() { return mpStartCalendarYear; }
    public void setMpStartCalendarYear(int val) { this.mpStartCalendarYear = val; }

    public boolean isMpBeetleImpact() { return mpBeetleImpact; }
    public void setMpBeetleImpact(boolean val) { this.mpBeetleImpact = val; }

    public int getMpBeetleDamageCounter() { return mpBeetleDamageCounter; }
    public void setMpBeetleDamageCounter(int val) { this.mpBeetleDamageCounter = val; }

    public int getMpBeetleRunNum() { return mpBeetleRunNum; }
    public void setMpBeetleRunNum(int val) { this.mpBeetleRunNum = val; }

    public int getMpBeetleStartYear() { return mpBeetleStartYear; }
    public void setMpBeetleStartYear(int val) { this.mpBeetleStartYear = val; }

}
