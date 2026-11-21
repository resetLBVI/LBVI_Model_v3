package lbvi.Utils;

import ec.util.MersenneTwisterFast;
import org.apache.commons.math3.special.Gamma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link DispersalKernal#sampleWeibull} (Weibull) for female-juvenile
 * dispersal and writes the draws to a CSV that the R scripts under analysis/R/
 * visualize.
 *
 * Parameters: shape_b = 1.22, scale_a = 5.49
 */
public class SampleWeibullTest {

    private static final double SHAPE_B = 1.22;   // shape
    private static final double SCALE_A = 5.49;   // scale
    private static final int    N       = 100_000;
    private static final long   SEED    = 42L;    // fixed seed -> reproducible draws

    private static final Path OUT = Paths.get("analysis", "data", "weibull_samples.csv");

    @Test
    @DisplayName("Write Weibull samples to CSV for R; all finite and non-negative")
    void writeSamplesForR() throws IOException {
        MersenneTwisterFast random = new MersenneTwisterFast(SEED);
        Files.createDirectories(OUT.getParent());

        boolean allValid = true;
        try (BufferedWriter w = Files.newBufferedWriter(OUT)) {
            w.write("x\n");
            for (int i = 0; i < N; i++) {
                double x = DispersalKernal.sampleWeibull(SHAPE_B, SCALE_A, random);
                if (!Double.isFinite(x) || x < 0) {
                    allValid = false;
                }
                w.write(Double.toString(x));
                w.write("\n");
            }
        }
        assertTrue(allValid, "Every Weibull sample should be finite and >= 0");
        System.out.println("Wrote " + N + " Weibull samples to " + OUT.toAbsolutePath());
    }

    @Test
    @DisplayName("Sample mean is close to the theoretical Weibull mean")
    void sampleMeanApproximatesTheoretical() {
        MersenneTwisterFast random = new MersenneTwisterFast(SEED);
        double sum = 0;
        for (int i = 0; i < N; i++) {
            sum += DispersalKernal.sampleWeibull(SHAPE_B, SCALE_A, random);
        }
        double mean = sum / N;
        // Weibull mean = scale * Gamma(1 + 1/shape)
        double theoretical = SCALE_A * Gamma.gamma(1.0 + 1.0 / SHAPE_B);
        assertTrue(Math.abs(mean - theoretical) < 0.20,
                "Sample mean " + mean + " should be near theoretical " + theoretical);
    }
}
