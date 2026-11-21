package lbvi.Cowbird;

import lbvi.LBVIEnvironment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link logisticFunForCapture#computeCaptureProb}.
 *
 * The environment defaults (P=0.90 at 0.9 km, P=0.10 at 3.0 km) reproduce column C
 * ("Intermediate alternative" / "in between") of
 * LogisticFunctionForCowbirdTrapEffectiveness.xlsx -- the red curve.
 */
public class logisticFunForCaptureTest {

    private static final long SEED = 42L;
    private static final Path OUT = Paths.get("analysis", "data", "capture_prob_curve.csv");

    private LBVIEnvironment state;

    @BeforeEach
    void setUp() {
        // Constructor is lightweight (GIS loading happens in start(), which we never call),
        // so this just gives us the default capture-distance parameters.
        state = new LBVIEnvironment(SEED);
    }

    @Test
    @DisplayName("Capture prob hits the two anchor probabilities at their defining distances")
    void anchorsAreExact() {
        double p90 = logisticFunForCapture.computeCaptureProb(state, state.mpCowbirdCaptureP90DistKM);
        double p10 = logisticFunForCapture.computeCaptureProb(state, state.mpCowbirdCaptureP10DistKM);
        assertEquals(0.90, p90, 1e-9, "P at the P90 distance should be 0.90");
        assertEquals(0.10, p10, 1e-9, "P at the P10 distance should be 0.10");
    }

    @Test
    @DisplayName("Prob is 0.5 at the midpoint distance between the two anchors")
    void midpointIsHalf() {
        double mid = (state.mpCowbirdCaptureP90DistKM + state.mpCowbirdCaptureP10DistKM) / 2.0;
        double p = logisticFunForCapture.computeCaptureProb(state, mid);
        assertEquals(0.5, p, 1e-9, "Logistic is symmetric: P=0.5 halfway between the 0.9 and 0.1 anchors");
    }

    @Test
    @DisplayName("Prob stays within (0,1) and decreases monotonically with distance")
    void boundedAndMonotonic() {
        double prev = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 100; i++) {          // 0.0 .. 5.0 km in 0.05 steps
            double d = i * 0.05;
            double p = logisticFunForCapture.computeCaptureProb(state, d);
            assertTrue(p > 0.0 && p < 1.0, "P(" + d + ")=" + p + " must be in (0,1)");
            assertTrue(p <= prev + 1e-12, "P must not increase with distance at d=" + d);
            prev = p;
        }
    }

    @Test
    @DisplayName("Reproduces the Excel 'Intermediate alternative' (red) curve values")
    void matchesExcelRedCurve() {
        // Expected values taken from column C of LogisticFunctionForCowbirdTrapEffectiveness.xlsx
        assertEquals(0.98338279621486846, logisticFunForCapture.computeCaptureProb(state, 0.0), 1e-6);
        assertEquals(0.87952768791197811, logisticFunForCapture.computeCaptureProb(state, 1.0), 1e-6);
        assertEquals(0.47386640141578484, logisticFunForCapture.computeCaptureProb(state, 2.0), 1e-6);
        assertEquals(0.10000000000000003, logisticFunForCapture.computeCaptureProb(state, 3.0), 1e-6);
    }

    @Test
    @DisplayName("Write the capture-probability curve to CSV for R plotting")
    void writeCurveForR() throws IOException {
        Files.createDirectories(OUT.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(OUT)) {
            w.write("x,prob\n");
            for (int i = 0; i <= 100; i++) {       // 0.0 .. 5.0 km in 0.05 steps
                double d = i * 0.05;
                double p = logisticFunForCapture.computeCaptureProb(state, d);
                w.write(Double.toString(d) + "," + Double.toString(p) + "\n");
            }
        }
        System.out.println("Wrote capture-probability curve to " + OUT.toAbsolutePath());
    }
}