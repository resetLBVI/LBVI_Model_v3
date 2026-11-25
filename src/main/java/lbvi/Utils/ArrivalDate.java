package lbvi.Utils;

import org.apache.commons.math3.distribution.PoissonDistribution; // retained for the commented-out old method below
import org.apache.commons.math3.distribution.TriangularDistribution;


/**
 * Helper for drawing each LBVI agent's arrival date (Julian day) each breeding season.
 *
 * CURRENT method ({@link #arrivalDate(int, int, int)}): the arrival date is sampled DIRECTLY from a
 * triangular distribution defined by a minimum {@code lower} (a), a mode {@code mode} (c), and a maximum
 * {@code upper} (b), then rounded to the nearest whole day. The triangular sample x is:
 *   if U < (c-a)/(b-a):  x = a + sqrt(U*(b-a)*(c-a))
 *   else:                x = b - sqrt((1-U)*(b-a)*(b-c))
 * The expected value is (a + c + b) / 3, and every draw lies in [a, b], so with a >= 0 the date is >= 0.
 *
 * SIMPLIFICATION (2026): the previous version used the triangular sample only as the lambda (mean) of a
 * Poisson distribution and returned a Poisson draw. That extra layer kept the same mean but added extra
 * dispersion and let dates fall outside [a, b]. The team decided the triangular draw alone is sufficient,
 * so the Poisson step was removed. The old two-step method is preserved (commented out) at the bottom of
 * this file in case the team wants it back; see CHANGELOG 2026-11-23.
 */

public class ArrivalDate {

    /**
     * Draw an arrival date directly from Triangular(lower, mode, upper), rounded to the nearest day.
     * The {@link TriangularDistribution} constructor validates the arguments and throws if they are not
     * ordered lower < mode-or-equal, mode <= upper, and lower < upper (same exceptions as before).
     *
     * @param lower minimum possible arrival date (a)
     * @param mode  most likely arrival date (c)
     * @param upper maximum possible arrival date (b)
     * @return a sampled arrival date (Julian day) in [lower, upper]
     */
    public static int arrivalDate(int lower, int mode, int upper) {
        TriangularDistribution triangularDistribution = new TriangularDistribution(lower, mode, upper);
        double sample = triangularDistribution.sample();
        return (int) Math.round(sample);
    }

    /* ---------------------------------------------------------------------------------------------------
     * OLD two-step method (triangular sample -> Poisson lambda -> Poisson draw). Replaced 2026-11-23 by the
     * direct triangular draw above. Kept here, commented out, in case the team wants to restore it. If you
     * restore it, note that this signature is identical to the active method above, so you must remove or
     * rename one of the two to avoid a duplicate-method compile error.
     *
     * public static int arrivalDate(int lower, int mode, int upper) {
     *     // Create an instance of TriangularDistribution
     *     TriangularDistribution triangularDistribution = new TriangularDistribution(lower, mode, upper);
     *     // Generate a random sample from this triangular distribution
     *     double lambda = triangularDistribution.sample();
     *     // Use the sampled lambda as the mean for a Poisson distribution
     *     PoissonDistribution poissonDistribution = new PoissonDistribution(lambda);
     *     int arrivalDate = (int) (poissonDistribution.sample());
     *     return arrivalDate;
     * }
     * ------------------------------------------------------------------------------------------------- */

}
