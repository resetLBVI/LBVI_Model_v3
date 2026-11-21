package lbvi.Utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ec.util.MersenneTwisterFast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampleInverseGaussianTest {

    private static final double MU = 2.66;      // mean  (female adult)
    private static final double LAMBDA = 0.33;  // shape
    private static final int    N = 100_000;
    private static final long   SEED = 42L;     // fixed seed -> reproducible draws

    private static final Path OUT = Paths.get("analysis", "data", "inverse_gaussian_samples.csv");

    @Test
    @DisplayName("Write Inverse Gaussian samples to CSV for R; all finite and positive")
    void writeSamplesForR() throws IOException {
        MersenneTwisterFast random = new MersenneTwisterFast(SEED);
        Files.createDirectories(OUT.getParent());

        boolean allValid = true;
        try (BufferedWriter w = Files.newBufferedWriter(OUT)) {
            w.write("x\n");
            for (int i = 0; i < N; i++) {
                double x = DispersalKernal.sampleInverseGaussian_SSJ(MU, LAMBDA, random);
                if (!Double.isFinite(x) || x <= 0) {
                    allValid = false;
                }
                w.write(Double.toString(x));
                w.write("\n");
            }
        }
        assertTrue(allValid, "Every sample should be finite and > 0");
        System.out.println("Wrote " + N + " Inverse Gaussian samples to " + OUT.toAbsolutePath());
    }

    @Test
    @DisplayName("All samples are finite and positive")
    void samplesArePositiveAndFinite() {
        MersenneTwisterFast random = new MersenneTwisterFast(42);  // fixed seed → deterministic, repeatable test
        boolean allValid = true;
        for (int i = 0; i < 10_000; i++) {
            double x = DispersalKernal.sampleInverseGaussian_SSJ(MU, LAMBDA, random);
            if (!Double.isFinite(x) || x <= 0) {
                allValid = false;
                break;
            }
        }
        assertTrue(allValid, "Every sample should be finite and > 0");
    }

    @Test
    @DisplayName("Sample mean is close to mu over many draws")
    void sampleMeanApproximatesMu() {
        MersenneTwisterFast random = new MersenneTwisterFast(42);
        int n = 200_000;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += DispersalKernal.sampleInverseGaussian_SSJ(MU, LAMBDA, random);
        }
        double mean = sum / n;
        // Inverse Gaussian mean == mu. Generous tolerance for Monte-Carlo noise.
        assertTrue(Math.abs(mean - MU) < 0.30,
                "Sample mean " + mean + " should be near mu=" + MU);
    }
}