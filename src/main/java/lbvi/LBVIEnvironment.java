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
    public String shpFilePath = "/RESET_LBVI_inputData/RESET_merge_final.shp"; //for background image use
    public String vegAttributePath = "/RESET_LBVI_inputData/RESET_merge_vegAttributes2020.csv";
    public String groundwaterFilePath = "RESET_LBVI_inputData/inGroundwaterBAU.csv";
    public String trapPlanFilePath = "RESET_LBVI_inputData/CowbirdTrappingPlanRangewide.csv";
    public String initVireoPopulationFilePath = "RESET_LBVI_inputData/inVireoPopulation2020.csv";
    public String tlbFilePath = "RESET_LBVI_inputData/";
    public String pshbFilePath = "RESET_LBVI_inputData/";
    //setup output files path
    public String logDebugFile = "logDebug.csv";
    public boolean debugLog = true; //master switch for per-agent debug logging (logDebug.csv); set false to disable
    public String logMortalityEventsFile = "logMortalityEvents.csv";
    public String logSuccessEventsFile = "logSuccessEvents.csv";
    public String logSurvivalOutcomesFile = "logSurvivalOutcomes.csv";
    public String logReproductivePerformanceFile = "logReproductivePerformance.csv";
    public String logDispersalDistanceFile = "logDispersalDistance.csv";
    public String logCowbirdArrivalOrDepartureFile = "logCowbirdArrivalOrDeparture.csv";
    public OutputWriter debugWriter; //this is a writer method class that helps us to wirte the logs
    public OutputWriter logMortalityWriter;
    public OutputWriter logSuccessWriter;
    public OutputWriter logSurvivalOutcomeWriter;
    public OutputWriter logReproductivePerformanceWriter;
    public OutputWriter logDispersalDistanceWriter;
    public OutputWriter logCowbirdArrivalOrDepartureWriter;
    //initialize grids to something sensible. For now, just placeholders; set sizes according to the world
    public final int worldWidth = 200; //WIDTH and HEIGHT correspond to arbitrary display dimensions
    public final int worldHeight = 200;
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
    // trap plan keyed by trapID; all years loaded upfront, filtered at runtime by trapYear
    public Map<Integer, TrapInfoIndentifier> trapInfo = new HashMap<>();
    // terrIDs of territories occupied at simulation start, loaded from initVireoPopulationFilePath
    public List<Integer> initVireoTerrIDs = new ArrayList<>();
    //agent's state variables
    public int lbviAgentID = 0;
    public Map<Integer, LBVIAgent> lbviAgentMap = new HashMap<>(); // vireoID -> agent, for cross-agent lookups
    public Map<Integer, Nest> nestMap = new HashMap<>();

    //LBVI arrival model parameters
    public int lowerArrivalDate = 91; //ODD: mpVireoFirstDate
    public int upperArrivalDate = 122; //ODD: mpVireoLastDate
    public int modeArrivalDate = 107; //ODD: mpVireoPeakDate
    //dispersal model parameters
    public double vireoBeyondKernalMAd = 20;
    public double vireoBeyondKernalFAd = 20;
    public double vireoBeyondKernalMJu = 20;
    public double vireoBeyondKernalFJu = 20;
    public double mpVireoTerrSearchDistanceM = 500; //buffer distance in meters
    public double mpVireoSearchDistanceCoefficient = 0.1;
    public double mpVireoLongDistanceDispersal = 0.001; //prob of disperse much longer distances than the maximum distances shown in dispersal kernels
    public double mpVireoLowerCutoffLDD = 20000; //
    public double mpVireoUpperCutoffLDD = 200000;
    //simulation control
    public int mpInputSimulationDurationYears = 30; //stop the simulation after this many years (1 year = 364 steps)
    //habitat selection model parameters
    public String mpHabitatQualityIndex = "terrQuality"; //patchQuality or terrQuality
    public int mpTerrSelectionTrait = 1; //0: habitat quality 1: mate availability 2: habitat then mate availability 3: mate availability then habitat 4: completely random
    //reproduction model variables - nest model parameters
    public double mpDailyProbNestDesertionBeforeEggLaying = 0.475; //false start probability
    public int mpVireoClutchSize = 5;
    public int mpMaxNumAttempts = 5;
    public int mpVireoNestBuildingDuration = 5; //a temporary default
    public int mpIncubationStageDuration = 14;
    public int mpNestlingStageDuration = 11;
    public int mpFledglingStageDuration = 25;
    public int mpRenestingIntervalDuration = 2;
    public int mpLastPossibleNestingDate = 169; //the last possible nesting date is set at the end of September
    public double mpProbVireoIsFemale = 0.5; //the probability of determining the newborn is a male or a female
    public int mpAgeUnderCowbirdRisk = 3; //vireo nest has eggs or chicks with an age in days less than this value can be parasite.
    //mortality model parameters
    public double mpDailyProbNestDesertionAfterEggLoss = 0.291; //in egg-laying stage, determine if the parents will abandon the nest
    public double mpDailyMortalityProbEachEggEggLaying = 0.036; //in egg-laying stage, determine the prob of each egg loss
    public double mpDailyMortalityProbEachEggIncubation = 0.029; //ODD_v3 3.11: mortality for each egg during the incubation stage
    public double mpDailyMortalityProbEachNestling = 0.029; //ODD_v3 3.12: mortality for each nestling during the nestling stage (baseline: 0 Cowbird nestlings)
    public double mpDailyMortalityProbEachFledgling = 0.02184; //ODD_v3 3.13: for each of its dependent Vireo and Cowbird fledglings, against this parameter to determine the DMR

    public double mpDailyMortalityProbWholeNestIncubation = 0.036; //ODDv3_3.11: mpDailyProbWholeNestMortalityPredatorDuringIncubation
    public double mpDailyMortalityProbWholeNestNestlingStage = 0.036; //ODDv3_3.12: mpDailyProbWholeNestMortalityPredatorDuringNestling (baseline: 0 Cowbird nestlings)


    //--- Hypothesis test (non-default): Cowbird-nestling-dependent predation during the NESTLING stage ---
    //When true, the whole-nest and per-Vireo-nestling predation probabilities during the NESTLING stage
    //vary with the number of Cowbird nestlings sharing the nest (0 / 1 / 2+); the per-nestling elevation
    //applies to Vireo nestlings only — Cowbird nestlings always die at the baseline rate. When false
    //(default), the two baseline rates above are always used, reproducing the current behavior. The
    //"0 Cowbird nestlings" case reuses the baselines: mpDailyNestlingMortality (whole nest) and
    //mpDailyProbNestlingMortalityPredatorDuringNestling (per nestling). Resolved in
    //LBVIAgent.wholeNestMortalityDuringNestling() / nestlingMortalityDuringNestling().
    public boolean mpTestCowbirdNestlingMortalityHypothesis = false;
    public double mpDailyMortalityProbWholeNestNestlingStage1BHCONestling = 0.0396;      //whole nest, exactly 1 Cowbird nestling
    public double mpDailyMortalityProbWholeNestNestlingStage2orMoreBHCONestlings = 0.04356; //whole nest, 2+ Cowbird nestlings
    public double mpDailyMortalityProbEachNestling1BHCONestling = 0.0319;        //per nestling, exactly 1 Cowbird nestling
    public double mpDailyMortalityProbEachNestling2orMoreBHCONestlings = 0.03509;  //per nestling, 2+ Cowbird nestlings
    //--- Hypothesis test (non-default): Cowbird-fledgling-dependent predation during the FLEDGLING stage ---
    //Fledglings have left the nest, so there is no whole-nest mortality here — only per-fledgling draws.
    //When true, the per-Vireo-fledgling predation probability varies with the number of Cowbird fledglings
    //sharing the brood (0 / 1 / 2+); Cowbird-fledgling draws always use the baseline. When false (default),
    //the baseline mpDailyProbFledglingMortalityPredator is always used. The "0 Cowbird fledglings" case
    //reuses that baseline. Resolved in LBVIAgent.fledglingMortalityPredator().
    public boolean mpTestCowbirdFledglingMortalityHypothesis = false;
    public double mpDailyMortalityProbEachFledgling1BHCOFledgling = 0.024024;        //per Vireo fledgling, exactly 1 Cowbird fledgling in brood
    public double mpDailyMortalityProbEachFledgling2orMoreBHCOFledglings = 0.026426; //per Vireo fledgling, 2+ Cowbird fledglings in brood
    public int mpVireoPopMaxAgeF = 12; //female maximum/death age (years); matches mpVireoStartPopFemaleAgeDist
    public int mpVireoPopMaxAgeM = 21; //male maximum/death age (years); matches mpVireoStartPopMaleAgeDist
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
    public double mpSurvivalProbJuvenileM = 0.5;
    public double mpSurvivalProbJuvenileF = 0.5;
    public double mpSurvivalProbAdultM = 0.9;
    public double mpSurvivalProbAdultF = 0.9;
    // Hyperparameters for the per-run survival-probability draws.
    // Gamma is parameterised as (shape, rate); Commons Math takes scale = 1/rate.  Beta is (alpha, beta).
    public double mpSurvivalProbJuvenileM_gammaShape = 156.45,  mpSurvivalProbJuvenileM_gammaRate = 267.30;
    public double mpSurvivalProbJuvenileF_gammaShape = 631.32,  mpSurvivalProbJuvenileF_gammaRate = 1467.25;
    public double mpSurvivalProbAdultM_betaAlpha = 132.94,      mpSurvivalProbAdultM_betaBeta = 34.55;
    public double mpSurvivalProbAdultF_betaAlpha = 132.94,      mpSurvivalProbAdultF_betaBeta = 34.55;
    public int mpNDaysChickThermoregulate = 5; //eggs and young chicks cannot survive sun exposure in the absence of tamarisk foliage, the young will die
    //cowbird state variable
    public int cowbirdID;
    //Cowbird parasitism
    public int mpNCowbirdEggsPerSeason = 30;
    public double mpMaxCowbirdArrivalProb = 0.046738;
    public double mpMinCowbirdArrivalProb = 0.0004;
    public int mpCowbirdFirstDate = 91; //Julian date
    public int mpPeakCowbirdDate = 105; //Julian date
    public int mpCowbirdLastDate = 154; //Julian date
    public double mpCowbirdNextNestIsVireo = 0.4; //value updated on Sept 7, 2026
    public double mpCowbirdNestSearchDistanceM = 1000; //value updated on Sep7, 2026
    public double mpCowbirdVireoEggRemovalProb = 0.588; //the prob that the vireo's egg removal and Cowbird egg ++
    //Cowbird capture
    public double mpMaxCowbirdCaptureDistM = 4500; //cowbird trap distance in meter
    public double mpCowbirdCaptureP90DistKM = 0.9; //distance (km) at which the distance-based capture probability = 0.90 (near trap, high prob)
    public double mpCowbirdCaptureP10DistKM = 3.0; //distance (km) at which the distance-based capture probability = 0.10 (far from trap, low prob)
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

    //initial population setting for testing (TEMPERARY)
    int initFemales = 5;
    int initMales = 5;


    public LBVIEnvironment(long seed) {
        super(seed);
        vegetationGrid = new SparseGrid2D(worldWidth, worldHeight);
        System.out.println("=+++++++=+++++++=+++++++=");
    }
    //end of Constructor


    public void start(){
        super.start();
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
        this.mpSurvivalProbAdultF = new BetaDistribution(
                rng, mpSurvivalProbAdultF_betaAlpha, mpSurvivalProbAdultF_betaBeta,
                BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY).sample();
        System.out.println("Per-run survival probs: juvM=" + mpSurvivalProbJuvenileM + ", juvF=" + mpSurvivalProbJuvenileF
                + ", adultM=" + mpSurvivalProbAdultM + ", adultF=" + mpSurvivalProbAdultF);
        try {
            /*
            ################    OUTPUT  ###########################
             */
            //(output 1) create debug file
            String[] debugHeader = {"currentStep", "Date", "vireoID", "sex", "ageClass", "LBVIStage", "arrivalDate", "currentLoc", "potentialTerrCount", "terrMaleID", "terrFemaleID", "mateID", "mateStatus"};
            logDebugFile = OutputWriter.getFileName(this.logDebugFile, false);
            this.debugWriter = new OutputWriter(logDebugFile);
            this.debugWriter.createFile(debugHeader);
            //(output 2) create logMortalityEvents
            String[] logMortalityHeader = {"step", "Date", "EventType", "TerrID", "VireoID", "NestID", "CowbirdID", "NumIndividual", "EntityType", "YoungSp"}; //currently collect 10 data
            logMortalityEventsFile = OutputWriter.getFileName(this.logMortalityEventsFile, false);
            this.logMortalityWriter = new OutputWriter(logMortalityEventsFile);
            this.logMortalityWriter.createFile(logMortalityHeader);
            //(output 3) create logSuccessEvents - annual summary for the nests
            String[] logSuccessHeader = {"currentStep", "Date", "EventType", "TerrID", "VireoID", "NestID", "NumIndividual", "EntityType", "YoungSp"}; //curently collect 9 data
            logSuccessEventsFile = OutputWriter.getFileName(this.logSuccessEventsFile, false);
            this.logSuccessWriter = new OutputWriter(logSuccessEventsFile);
            this.logSuccessWriter.createFile(logSuccessHeader);
            //(output 4) create logSurvivalOutcomes
//            String[] logSurvivalOutcomeHeader = {};
//            logSurvivalOutcomesFile = OutputWriter.getFileName(this.logSurvivalOutcomesFile, false);
//            this.logSurvivalOutcomeWriter = new OutputWriter(logSurvivalOutcomesFile);
//            this.logSurvivalOutcomeWriter.createFile(logSurvivalOutcomeHeader);
            //(output 5) create logReproductivePerformance - it's a population level summary for each year
            //13 attributes
            String[] logReproductivePerformanceHeader = {"year", "populationSize", "NumPair", "NumSingles", "NumNests", "NumEggs", "NumNestlings", "NumFledglings", "NumIndependentFledglings", "NumSuccessfulNests", "NumCowbirdArrival", "NumParasitism", "NumCowbirdCaptured"};
            logReproductivePerformanceFile = OutputWriter.getFileName(this.logReproductivePerformanceFile, false);
            this.logReproductivePerformanceWriter = new OutputWriter(logReproductivePerformanceFile);
            this.logReproductivePerformanceWriter.createFile(logReproductivePerformanceHeader);
            //(output 6) create logDispersalDistance
            String[] logDispersalDistanceHeader = {"currentStep", "Date", "terrID", "vireoID", "PrevLoc", "Distance"}; //determine the fields based on 2026-08-04 ODD; collect
            logDispersalDistanceFile = OutputWriter.getFileName(this.logDispersalDistanceFile, false);
            this.logDispersalDistanceWriter = new OutputWriter(logDispersalDistanceFile);
            this.logDispersalDistanceWriter.createFile(logDispersalDistanceHeader);
            //(output 7) create logCowbirdArrivalOrDeparture
            String[] logCowbirdArrivalOrDepartureHeader = {"currentStep", "date", "terrID", "CowbirdID", "ArrivalOrDepature"};
            logCowbirdArrivalOrDepartureFile = OutputWriter.getFileName(this.logCowbirdArrivalOrDepartureFile, false);
            this.logCowbirdArrivalOrDepartureWriter = new OutputWriter(logCowbirdArrivalOrDepartureFile);
            this.logCowbirdArrivalOrDepartureWriter.createFile(logCowbirdArrivalOrDepartureHeader);


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
        //(input 5) load tlb impact input (csv)

        //(input 6) load pshb impact input (csv): input "year", "week", "deadVegetation" "x"	"y"	"patchID"

        //(input 7) load initial LBVI population file
        try {
            initVireoPopulationFilePath = OutputWriter.getFileName(this.initVireoPopulationFilePath, true);
            loadInitVireoPopulation(initVireoPopulationFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Fail to load initial Vireo population CSV", e);
        }
        //(10) initiate timer just to update the time
        LBVITimer systemTimer = new LBVITimer();
        // Run the clock EVERY step, at ordering -1 so it fires before the observer (0) and the agents (1)
        // and they all read a fresh currentYear / currentJulianDay this step. (Was scheduleOnce, which froze
        // the clock at day 0 / year 0 for the whole run.)
        schedule.scheduleRepeating(Schedule.EPOCH, -1, systemTimer);
        //(11) Make agents
        initAgents();
        //(12) initiate observer
        LBVIObserver observer = new LBVIObserver();
        schedule.scheduleRepeating(observer);
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

                    // convert centroid coordinates to model grid location (COORD_X_y/COORD_Y_y = TerrID centroid)
                    Number pxAttr = (Number) f.getAttribute("COORD_X_y");
                    Number pyAttr = (Number) f.getAttribute("COORD_Y_y");
                    double pointX = (pxAttr == null) ? 0.0 : pxAttr.doubleValue();
                    double pointY = (pyAttr == null) ? 0.0 : pyAttr.doubleValue();
                    int col = worldXToGrid(pointX);
                    int row = worldYToGrid(pointY);
                    this.territoryLocations.put(terrID, new Int2D(col, row));
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
     * Expected CSV columns (with header row):
     *   PatchID, TerrID, CoordX, CoordY, Acres, MapCode, VegName,
     *   TWDens, SWMFDens, ArundoDens, TamarDens, Quality1
     *
     * @param csvPath path to the vegetation attribute CSV file (vegAttributePath)
     */
    public void loadVegAttributes(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // skip header row
            if (line == null) throw new IOException("Vegetation attribute CSV is empty: " + csvPath);
            int count = 0;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",", -1);
                for (int i = 0; i < cols.length; i++) cols[i] = cols[i].trim().replace("\"", "");
                // columns: PatchID[0], TerrID[1], CoordX[2], CoordY[3], Acres[4],
                //          MapCode[5], VegName[6], TWDens[7], SWMFDens[8] (skipped),
                //          ArundoDens[9], TamarDens[10], Quality1[11]
                int    patchID    = Integer.parseInt(cols[0]);
                int    terrID     = Integer.parseInt(cols[1]);
                double coordX     = Double.parseDouble(cols[2]);
                double coordY     = Double.parseDouble(cols[3]);
                double acres      = Double.parseDouble(cols[4]);
                int    mapCode    = Integer.parseInt(cols[5]);
                String vegName    = cols[6];
                double twDens     = Double.parseDouble(cols[7]);
                double swmfDens   = Double.parseDouble(cols[8]);
                double arundoDens = Double.parseDouble(cols[9]);
                double tamarDens  = Double.parseDouble(cols[10]);
                double quality1   = Double.parseDouble(cols[11]);

                Geometry geom   = terrGeometries.get(terrID);
                Int2D    gridLoc = territoryLocations.get(terrID);
                if (geom == null || gridLoc == null) {
                    System.err.println("loadVegAttributes: no shapefile entry for terrID " + terrID + ", skipping");
                    continue;
                }
                vegTerrInfo.put(terrID, new VegInfoIdentifier(
                        patchID, terrID, coordX, coordY, acres, mapCode, vegName,
                        twDens, swmfDens, arundoDens, tamarDens, quality1, geom, gridLoc));
                vegPatchInfo.put(patchID, new VegInfoIdentifier(patchID, terrID, coordX, coordY, acres,
                        mapCode, vegName, twDens, swmfDens, arundoDens, tamarDens, quality1, geom, gridLoc));
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
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("Groundwater CSV is empty: " + csvPath);
            // build column-name → index map from header
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim(), i);
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
     * Reads cowbird trap plan data from a CSV file and populates trapInfo (keyed by trapID).
     * All years are loaded upfront. Use logisticFunForCapture.findOpenTraps(state) at runtime
     * to filter to traps that are open on the current simulation day.
     *
     * Expected CSV columns (with header row):
     *   POINT_X, POINT_Y, TrapID, Year, OpenDate, CloseDate, NDaysClose
     *   OpenDate and CloseDate are Julian day numbers.
     *
     * @param csvPath path to the trap plan CSV (trapPlanFilePath)
     */
    public void loadTrapPlan(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("Trap plan CSV is empty: " + csvPath);
            // build column-name → index map from header
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim(), i);
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
                int    openDate     = Integer.parseInt(v[col.get("OpenDate")]);
                int    closeDate    = Integer.parseInt(v[col.get("CloseDate")]);
                int    nDaysClosed  = Integer.parseInt(v[col.get("NDaysClose")]);
                trapInfo.put(trapID, new TrapInfoIndentifier(xCoord, yCoord, trapID, year, openDate, closeDate, nDaysClosed));
                count++;
            }
            System.out.println("Loaded trap records: " + count);
        }
    }

    /**
     * Reads the initial Vireo population from a single-column CSV and populates initVireoTerrIDs.
     * Each row represents one territory occupied at simulation start.
     *
     * Expected CSV column (with header row):
     *   terrID
     *
     * @param csvPath path to the initial Vireo population CSV (initVireoPopulationFilePath)
     */
    public void loadInitVireoPopulation(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("Init Vireo population CSV is empty: " + csvPath);
            if (headerLine.startsWith("﻿")) headerLine = headerLine.substring(1); // strip UTF-8 BOM
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < headers.length; i++) col.put(headers[i].trim(), i);
            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] v = line.split(",", -1);
                for (int i = 0; i < v.length; i++) v[i] = v[i].trim().replace("\"", "");
                int terrID = Integer.parseInt(v[col.get("TerrID")]);
                if (!territoryLocations.containsKey(terrID)) {
                    System.err.println("loadInitVireoPopulation: terrID " + terrID + " not in shapefile, skipping");
                    continue;
                }
                initVireoTerrIDs.add(terrID);
                count++;
            }
            System.out.println("Loaded initial Vireo territories: " + count);
        }
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

            LBVIAgent male = makeAgent(false, true, terrID);
            male.event = schedule.scheduleRepeating(Schedule.EPOCH, 1, male);
            vegetationGrid.setObjectLocation(male, loc.x, loc.y);
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

}
