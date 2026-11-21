package lbvi.Utils;

import ec.util.MersenneTwisterFast;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link DispersalKernal#sampleBivariateT} (2Dt) for male-adult and
 * male-juvenile dispersal and writes the draws to CSVs that the R scripts under
 * analysis/R/ visualize.
 *
 * Parameters:
 *   male adult:    a = 0.078, b = 1.47
 *   male juvenile: a = 3.33,  b = 2.41
 *
 * NOTE: for b <= 1.5 (male adult) the 2Dt distance kernel has an INFINITE mean
 * (heavy tail), so this test validates the sample MEDIAN — which always exists —
 * rather than the mean.
 */
public class SampleBivariateTTest {

    private static final int  N    = 100_000;
    private static final long SEED = 42L;   // fixed seed -> reproducible draws

    @Test
    @DisplayName("Male adult (a=0.078, b=1.47): valid draws, median near theoretical, CSV written")
    void maleAdult() throws IOException {
        runCase(0.078, 1.47, Paths.get("analysis", "data", "2Dt_male_adult.csv"));
    }

    @Test
    @DisplayName("Male juvenile (a=3.33, b=2.41): valid draws, median near theoretical, CSV written")
    void maleJuvenile() throws IOException {
        runCase(3.33, 2.41, Paths.get("analysis", "data", "2Dt_male_juvenile.csv"));
    }

    private void runCase(double a, double b, Path out) throws IOException {
        MersenneTwisterFast random = new MersenneTwisterFast(SEED);
        Files.createDirectories(out.getParent());

        double[] samples = new double[N];
        boolean allValid = true;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("x\n");
            for (int i = 0; i < N; i++) {
                double x = DispersalKernal.sampleBivariateT(a, b, random);
                if (!Double.isFinite(x) || x < 0) {
                    allValid = false;
                }
                samples[i] = x;
                w.write(Double.toString(x));
                w.write("\n");
            }
        }
        assertTrue(allValid, "Every 2Dt sample should be finite and >= 0");

        // Theoretical median of the 2Dt distance kernel:
        //   F(x) = 1 - (1 + (x/a)^2)^(-(b-1)) = 0.5
        //   => x = a * sqrt(2^(1/(b-1)) - 1)
        double theoreticalMedian = a * Math.sqrt(Math.pow(2.0, 1.0 / (b - 1)) - 1.0);
        Arrays.sort(samples);
        double sampleMedian = samples[N / 2];
        assertTrue(Math.abs(sampleMedian - theoreticalMedian) < 0.10 * theoreticalMedian,
                "Sample median " + sampleMedian + " should be near theoretical " + theoreticalMedian);
        System.out.println("Wrote " + N + " 2Dt samples (a=" + a + ", b=" + b + ") to " + out.toAbsolutePath());
    }
}
