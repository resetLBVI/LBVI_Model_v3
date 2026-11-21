package lbvi.Utils;

import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class testArrivalDate {
    @Test
    @DisplayName("Samples should be non-negative")
    void samplesAreNonNegative() {
        int lower = 79, mode = 110, upper = 130;

        boolean allNonNegative = IntStream.generate(() -> ArrivalDate.arrivalDate(lower, mode, upper))
                .limit(5_000)  // plenty for a quick sanity check
                .allMatch(x -> x >= 0);

        assertTrue(allNonNegative, "All arrival dates should be >= 0");
    }

    @Test
    @DisplayName("Empirical mean ≈ expected triangular mean")
    void sampleMeanMatchesExpectedWithinTolerance() {
        int lower = 79, mode = 110, upper = 130;

        // Theoretical E[lambda] for Triangular(a, c, b) is (a + c + b) / 3
        double expectedMean = (lower + mode + upper) / 3.0;

        IntSummaryStatistics stats = IntStream.generate(() -> ArrivalDate.arrivalDate(lower, mode, upper))
                .limit(50_000)  // large enough to make the mean stable
                .summaryStatistics();

        double empiricalMean = stats.getAverage();

        // Generous ±3 window to keep the test stable across runs/platforms
        assertTrue(empiricalMean > expectedMean - 3 && empiricalMean < expectedMean + 3,
                "Empirical mean " + empiricalMean + " not within tolerance of expected " + expectedMean);
    }

    @Test
    @DisplayName("Invalid (a, c, b) ordering throws")
    void invalidParametersThrow() {
        // mode < lower
        assertThrows(NumberIsTooSmallException.class,
                () -> ArrivalDate.arrivalDate(79, 50, 130));

        // mode > upper
        assertThrows(NumberIsTooLargeException.class,
                () -> ArrivalDate.arrivalDate(79, 140, 130));

        // lower >= upper
        assertThrows(NumberIsTooLargeException.class,
                () -> ArrivalDate.arrivalDate(79, 80, 79));
    }

    @Test
    @DisplayName("Write arrival-date samples to CSV for R; all non-negative")
    void writeSamplesForR() throws IOException {
        int lower = 95, mode = 113, upper = 144;   // same params the old main() used
        final int N = 100_000;

        Path out = Paths.get("analysis", "data", "arrivalDate_samples.csv");
        Files.createDirectories(out.getParent());

        boolean allValid = true;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("x\n");                          // single "x" column, matches the R read pattern (df$x)
            for (int i = 0; i < N; i++) {
                int x = ArrivalDate.arrivalDate(lower, mode, upper);
                if (x < 0) {
                    allValid = false;
                }
                w.write(Integer.toString(x));
                w.write("\n");
            }
        }
        assertTrue(allValid, "Every arrival date should be >= 0");
        System.out.println("Wrote " + N + " arrival-date samples to " + out.toAbsolutePath());
    }
}
