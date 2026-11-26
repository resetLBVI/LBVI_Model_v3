package lbvi.Beetles;

import lbvi.LBVIEnvironment;
import lbvi.Utils.OutputWriter;
import lbvi.VegHealth;
import lbvi.VegInfoIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sim.util.Int2D;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PSHB beetle impact (ODD 3.1) and recovery (ODD 3.22): the loader's runNum filter and
 * hit.year -> simulation-year offset mapping, the damage mutation, and the season-end recovery countdown.
 * The LBVIEnvironment constructor is lightweight (GIS loads in start(), never called here), so we inject
 * vegTerrInfo directly and drive the static ActBeetleImpact methods.
 */
public class ActBeetleImpactTest {

    private LBVIEnvironment state;

    @BeforeEach
    void setUp() {
        state = new LBVIEnvironment(42L);
        state.mpBeetleRunNum = 1;
        state.mpBeetleStartYear = 1985; // 1985 -> sim year 1
        state.mpBeetleDamageCounter = 4;
        // two territories known to the model: 100 (quality 5, healthy) and 101 (quality 7, healthy)
        state.vegTerrInfo.put(100, terr(100, 5.0));
        state.vegTerrInfo.put(101, terr(101, 7.0));
    }

    private static VegInfoIdentifier terr(int id, double quality) {
        return new VegInfoIdentifier(id, id, 0.0, 0.0, 1.0, 0, "MU", "veg",
                0, 0, 0, 0, quality, VegHealth.HEALTHY, null, new Int2D(0, 0));
    }

    private Path writeCsv(String body) throws IOException {
        Path p = Files.createTempFile("inBeetleImpact_test", ".csv");
        try (BufferedWriter w = Files.newBufferedWriter(p)) { w.write(body); }
        p.toFile().deleteOnExit();
        return p;
    }

    @Test
    @DisplayName("Loader keeps only the selected runNum, offset-maps hit.year, and skips unknown terrIDs")
    void loaderFiltersAndMaps() throws IOException {
        // quoted header like the real PSHB files; mix of runNums, years, and one unknown terr (999)
        Path csv = writeCsv("\"runNum\",\"hit.year\",\"hit.terr\"\n"
                + "1,1985,100\n"   // runNum 1, sim year 1
                + "1,1986,101\n"   // runNum 1, sim year 2
                + "2,1985,100\n"   // other replicate -> ignored
                + "1,1985,999\n"); // unknown terr -> skipped
        state.loadBeetleImpact(csv.toString());

        assertEquals(2, state.beetleImpactByYear.size(), "only runNum 1, known terrs, 2 sim years");
        assertEquals(List.of(100), state.beetleImpactByYear.get(1), "1985 -> sim year 1");
        assertEquals(List.of(101), state.beetleImpactByYear.get(2), "1986 -> sim year 2");
    }

    @Test
    @DisplayName("Empty/blank path is a no-op (feature off), leaving no beetle impacts")
    void blankPathIsOff() throws IOException {
        state.loadBeetleImpact("");
        assertTrue(state.beetleImpactByYear.isEmpty());
    }

    @Test
    @DisplayName("Damage sets very-stressed(1)+quality 0, saves pre-damage quality, starts the counter")
    void damageMutatesTerritory() {
        state.beetleImpactByYear.put(1, List.of(100));
        state.currentYear = 1;
        ActBeetleImpact.applyAnnualDamage(state);

        VegInfoIdentifier t = state.vegTerrInfo.get(100);
        assertEquals(VegHealth.SEVERELYSTRESSED.getCode(), t.terrVegHealth, "very stressed = code 1");
        assertEquals(0, t.terrQuality, "quality zeroed");
        assertEquals(5, t.terrPreBeetleDamageQuality, "pre-damage quality saved");
        assertEquals(4, t.terrBeetleDamage, "counter = mpBeetleDamageCounter");
        // territory 101 is not listed this year -> untouched
        assertEquals(7, state.vegTerrInfo.get(101).terrQuality);
    }

    @Test
    @DisplayName("Recovery decrements each season and restores health+quality when the counter hits 0")
    void recoveryRestoresAfterCountdown() {
        state.beetleImpactByYear.put(1, List.of(100));
        state.currentYear = 1;
        ActBeetleImpact.applyAnnualDamage(state);
        VegInfoIdentifier t = state.vegTerrInfo.get(100);

        // counter starts at 4: three decrements leave it damaged, the fourth recovers it
        for (int i = 0; i < 3; i++) {
            ActBeetleImpact.recoverFromBeetleDamage(state);
            assertEquals(0, t.terrQuality, "still damaged while counter > 0");
            assertTrue(t.terrBeetleDamage > 0);
        }
        ActBeetleImpact.recoverFromBeetleDamage(state); // counter 1 -> 0
        assertEquals(0, t.terrBeetleDamage);
        assertEquals(VegHealth.HEALTHY.getCode(), t.terrVegHealth, "recovered to healthy = code 3");
        assertEquals(5, t.terrQuality, "quality restored from pre-damage value");
    }

    @Test
    @DisplayName("Recovery never touches never-damaged territories sitting at counter 0")
    void recoveryIgnoresUndamaged() {
        VegInfoIdentifier t = state.vegTerrInfo.get(101); // counter 0, quality 7, never damaged
        ActBeetleImpact.recoverFromBeetleDamage(state);
        assertEquals(7, t.terrQuality, "undamaged territory must not be reset to a zero pre-damage value");
        assertEquals(VegHealth.HEALTHY.getCode(), t.terrVegHealth);
        assertEquals(0, t.terrBeetleDamage);
    }

    @Test
    @DisplayName("TEMP diagnostic log traces a damaged territory from damage through recovery when enabled")
    void diagnosticLogTracesTrajectory() throws IOException {
        // enable the diagnostic writer to a fresh temp file (createNewFile needs it NOT to exist yet)
        Path dir = Files.createTempDirectory("beetlelog");
        Path out = dir.resolve("logBeetleImpact.csv");
        state.beetleImpactWriter = new OutputWriter(out.toString());
        state.beetleImpactWriter.createFile(new String[]{"currentStep", "Year", "Date", "terrID",
                "terrQuality", "terrVegHealth", "terrBeetleDamage", "terrPreBeetleDamageQuality"});

        state.beetleImpactByYear.put(1, List.of(100));
        state.currentYear = 1;
        ActBeetleImpact.applyAnnualDamage(state);                 // 1 damage row
        for (int i = 0; i < 4; i++) ActBeetleImpact.recoverFromBeetleDamage(state); // 4 season-end rows

        List<String> lines = Files.readAllLines(out);
        out.toFile().deleteOnExit();
        dir.toFile().deleteOnExit();
        assertEquals(1 + 5, lines.size(), "header + damage row + 4 season-end rows");
        // first data row = moment of damage: terr 100, quality 0, very stressed (1), counter 4, pre-damage saved 5
        assertEquals("100,0,1,4,5", dataCols(lines.get(1)), "damage row terrID,Quality,VegHealth,BeetleDamage,PreDmg");
        // last data row = recovery: terr 100, quality restored to 5, healthy (3), counter 0, pre-damage 5
        assertEquals("100,5,3,0,5", dataCols(lines.get(5)), "recovery row terrID,Quality,VegHealth,Damage,PreDmg");
    }

    /** Returns the terrID,terrQuality,terrVegHealth,terrBeetleDamage,terrPreBeetleDamageQuality columns (4..8). */
    private static String dataCols(String csvLine) {
        String[] c = csvLine.split(",");
        return c[3] + "," + c[4] + "," + c[5] + "," + c[6] + "," + c[7];
    }
}
