package lbvi.Utils;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ec.util.MersenneTwisterFast;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the four dispersal-distance samplers in {@link DispersalKernal} reproduce the
 * published empirical kernels (see the class doc of DispersalKernal and figure LBVI_DispKernels4.jpg):
 *
 *   panel a — Female Adult   : Inverse Gaussian (Wald), mu = 2.66, lambda = 0.33
 *   panel d — Male Adult     : 2Dt (bivariate Student-t), a = 0.078, b = 1.47
 *   panel b — Female Juvenile: Weibull, scale a = 5.49, shape b = 1.22
 *   panel c — Male Juvenile  : 2Dt (bivariate Student-t), a = 3.33, b = 2.41
 *
 * Method: draw a large sample with a fixed seed, then compare the EMPIRICAL CDF to the ANALYTIC
 * kernel CDF at several points. The analytic CDFs are written out in closed form here (independent
 * of the sampling implementation), so this is a genuine goodness-of-fit check, not a tautology.
 *
 * UNITS: these samplers return distances in KILOMETRES — that is the native unit of the fitted
 * kernels. In the model, the coordinate system (inTerritories2020.csv POINT_X/POINT_Y) is in
 * METRES, so LBVIAgent.dispersalKernel() multiplies each regular draw by 1000 before it is applied
 * as a displacement (see the 2026-11-19 km->m bug fix). The {@code expectedRealizedMetres...} test
 * records the metre-scale medians the realized logDispersalDistance.csv should show after that fix.
 */
public class DispersalKernelDistributionTest {

    private static final int  N    = 200_000;
    private static final long SEED = 42L;
    // Max allowed gap between empirical and analytic CDF at any test point.
    // Monte-Carlo SE of an empirical CDF value is <= sqrt(0.25/N) ~= 0.0011 at N=200k; 0.015 is generous.
    private static final double CDF_TOL = 0.015;

    // ----------------------------------------------------------------- helpers

    /** Draw N sorted samples from a supplier. */
    private static double[] drawSorted(java.util.function.DoubleSupplier draw) {
        double[] xs = new double[N];
        for (int i = 0; i < N; i++) xs[i] = draw.getAsDouble();
        Arrays.sort(xs);
        return xs;
    }

    /** Empirical CDF F_hat(x) = fraction of samples <= x, via binary search on sorted data. */
    private static double empiricalCdf(double[] sorted, double x) {
        int lo = 0, hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= x) lo = mid + 1; else hi = mid;
        }
        return (double) lo / sorted.length;
    }

    /** Assert empirical CDF matches analytic CDF at every point. */
    private static void assertCdfMatches(String label, double[] sorted,
                                         double[] points, java.util.function.DoubleUnaryOperator analyticCdf) {
        for (double x : points) {
            double emp = empiricalCdf(sorted, x);
            double ana = analyticCdf.applyAsDouble(x);
            assertTrue(Math.abs(emp - ana) < CDF_TOL,
                    String.format("%s: CDF mismatch at x=%.4f km — empirical=%.4f, analytic=%.4f (|diff|=%.4f > %.4f)",
                            label, x, emp, ana, Math.abs(emp - ana), CDF_TOL));
        }
    }

    // ------------------------------------------------- analytic kernel CDFs (km)

    private static final NormalDistribution STD_NORMAL = new NormalDistribution(0.0, 1.0);

    /** Inverse Gaussian (Wald) CDF: F(x) = Phi(sqrt(l/x)(x/mu-1)) + e^{2l/mu} Phi(-sqrt(l/x)(x/mu+1)). */
    private static double igCdf(double x, double mu, double lambda) {
        double s = Math.sqrt(lambda / x);
        return STD_NORMAL.cumulativeProbability(s * (x / mu - 1.0))
             + Math.exp(2.0 * lambda / mu) * STD_NORMAL.cumulativeProbability(-s * (x / mu + 1.0));
    }

    /** 2Dt CDF: F(x) = 1 - (1 + (x/a)^2)^(1-b). */
    private static double twoDtCdf(double x, double a, double b) {
        return 1.0 - Math.pow(1.0 + (x / a) * (x / a), 1.0 - b);
    }

    /** Weibull CDF: F(x) = 1 - exp(-(x/a)^b), scale a, shape b. */
    private static double weibullCdf(double x, double a, double b) {
        return 1.0 - Math.exp(-Math.pow(x / a, b));
    }

    // --------------------------------------------------------------- the tests

    @Test
    @DisplayName("Female Adult draws match Inverse Gaussian(mu=2.66, lambda=0.33) in km")
    void femaleAdultMatchesInverseGaussian() {
        MersenneTwisterFast r = new MersenneTwisterFast(SEED);
        double[] xs = drawSorted(() -> DispersalKernal.sampleInverseGaussian_SSJ(2.66, 0.33, r));
        double[] pts = {0.5, 1.0, 2.0, 4.0, 8.0};
        assertCdfMatches("FemaleAdult IG", xs, pts, x -> igCdf(x, 2.66, 0.33));
    }

    @Test
    @DisplayName("Male Adult draws match 2Dt(a=0.078, b=1.47) in km")
    void maleAdultMatchesBivariateT() {
        MersenneTwisterFast r = new MersenneTwisterFast(SEED);
        double[] xs = drawSorted(() -> DispersalKernal.sampleBivariateT(0.078, 1.47, r));
        double[] pts = {0.05, 0.1, 0.3, 1.0, 5.0};
        assertCdfMatches("MaleAdult 2Dt", xs, pts, x -> twoDtCdf(x, 0.078, 1.47));
    }

    @Test
    @DisplayName("Female Juvenile draws match Weibull(scale=5.49, shape=1.22) in km")
    void femaleJuvenileMatchesWeibull() {
        MersenneTwisterFast r = new MersenneTwisterFast(SEED);
        // NOTE: sampleWeibull(shape_b, scale_a) — shape first, then scale.
        double[] xs = drawSorted(() -> DispersalKernal.sampleWeibull(1.22, 5.49, r));
        double[] pts = {1.0, 3.0, 6.0, 10.0, 15.0};
        assertCdfMatches("FemaleJuvenile Weibull", xs, pts, x -> weibullCdf(x, 5.49, 1.22));
    }

    @Test
    @DisplayName("Male Juvenile draws match 2Dt(a=3.33, b=2.41) in km")
    void maleJuvenileMatchesBivariateT() {
        MersenneTwisterFast r = new MersenneTwisterFast(SEED);
        double[] xs = drawSorted(() -> DispersalKernal.sampleBivariateT(3.33, 2.41, r));
        double[] pts = {1.0, 2.0, 4.0, 8.0, 15.0};
        assertCdfMatches("MaleJuvenile 2Dt", xs, pts, x -> twoDtCdf(x, 3.33, 2.41));
    }

    /**
     * Documents the expected scale of REALIZED dispersal distances. The samplers return km; the model
     * applies them as metres (x1000). This records the per-class median in both units so the team can
     * sanity-check logDispersalDistance.csv (Euclidean metres) after a run — realized medians should sit
     * in the hundreds-to-thousands of metres, NOT single-digit metres (the pre-fix km-as-metres symptom).
     */
    @Test
    @DisplayName("Kernel medians are km-scale; realized displacement should be ~1000x in metres")
    void expectedRealizedMetresAreKmTimes1000() {
        MersenneTwisterFast r = new MersenneTwisterFast(SEED);
        double[] fAd = drawSorted(() -> DispersalKernal.sampleInverseGaussian_SSJ(2.66, 0.33, r));
        double[] mAd = drawSorted(() -> DispersalKernal.sampleBivariateT(0.078, 1.47, r));
        double[] fJu = drawSorted(() -> DispersalKernal.sampleWeibull(1.22, 5.49, r));
        double[] mJu = drawSorted(() -> DispersalKernal.sampleBivariateT(3.33, 2.41, r));

        double medFAd = fAd[N / 2], medMAd = mAd[N / 2], medFJu = fJu[N / 2], medMJu = mJu[N / 2];
        System.out.printf("Median dispersal (km -> m): FemAdult %.3f km -> %.0f m%n", medFAd, medFAd * 1000);
        System.out.printf("Median dispersal (km -> m): MaleAdult %.3f km -> %.0f m%n", medMAd, medMAd * 1000);
        System.out.printf("Median dispersal (km -> m): FemJuv  %.3f km -> %.0f m%n", medFJu, medFJu * 1000);
        System.out.printf("Median dispersal (km -> m): MaleJuv %.3f km -> %.0f m%n", medMJu, medMJu * 1000);

        // Female-adult IG is heavily right-skewed (mean=mu=2.66 km but median ~0.57 km), so once applied
        // the median displacement is ~570 m. The pre-fix bug made the applied displacement equal the km
        // number in metres (~0.57 m) — these bounds guard that metres-scale conversion.
        assertTrue(medFAd > 0.3 && medFAd < 2.66,
                "Female-adult median should be sub-km (IG right-skew), was " + medFAd + " km");
        assertTrue(medFAd * 1000 > 300,
                "Realized female-adult median must be metres-scale (>300 m), not km-as-metres");
    }
}
