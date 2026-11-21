package lbvi.Utils;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.WeibullDistribution;
import umontreal.ssj.probdist.InverseGaussianDist;

import ec.util.MersenneTwisterFast;

/**
 * Dispersal distance kernels were fit using weighted maximum likelihood estimation. The density function form
 * was determined using the dispfit package in R. The weights of individual observations were determine using
 * methods previously described and are based upon representation of distance bands according to the number of
 * likely ‘departure points’. The solution space was described and visualized with a likelihood surface across
 * the parameter solution space to check for and avoid local minima. The best set of parameters were then used
 * as initial values in the optim procedure in R. In all cases the inclusion of the weightings either did not
 * change the parameter estimates or changed them slightly (by digits 1 or 2) in the third significant
 * figure/second decimal place. This shows that the initial selection of the density function form without the
 * weights in dispfit, was robust to later including the weights.
 *
 * Female Adults
 * 153 observations, 3 > 20 km:  24, 25, 29 km.
 * Wald density, mu=2.66, lambda=0.33
 * Wald:  sqrt(lambda/(2*pi*x^3)) * exp(-(lambda*(x-mu)^2)/(2*mu^2*x))
 *
 * Male Adults
 * 729 observations, 12 obs > 20 km:  24, 24, 25, 35, 36, 40, 63, 72, 79, 84, 104, 158 km
 * Bivariate student’s t density, a=0.078, b=1.47
 * 2Dt:  (2*x*(b-1)/(a^2)) * (1+(x/a)^2)^-b
 *
 * Female Juveniles
 * 46 observations, 0 > 20 km
 * Weibull density, a=5.49, b=1.22
 * Weibull:  (b/a^b) * x^(b-1) * exp(-1*(x/a)^b)
 *
 * Male Juveniles
 * 163 observations, 8 obs > 20 km: 22,23,25,26,61,89,97,105 km
 * Bivariate student’s t density, a=3.33, b=2.41
 * 2Dt: (2*x*(b-1))/(a^2) * ( 1 + (x/a)^2 )^(-b)
 */
public class DispersalKernal {

    /**
     * The Inverse Gaussian Distribution is used to sample a random distance for female adults.
     *
     * Sampling uses the Michael-Schucany-Haas algorithm. Given a standard normal
     * variate v ~ N(0, 1) and y = v^2, the candidate is:
     *
     *   x = mu + (mu^2 * y) / (2 * lambda)
     *         - (mu / (2 * lambda)) * sqrt(4 * mu * lambda * y + mu^2 * y^2)
     *
     * Then draw u ~ U(0, 1) and return x if u <= mu / (mu + x), otherwise mu^2 / x.
     * This form guarantees x > 0 for all y >= 0.
     *
     * @param mu     mean of the Inverse Gaussian distribution
     * @param lambda shape parameter
     * @param random source of randomness
     * @return a sample from IG(mu, lambda)
     */
    public static double sampleInverseGaussian(double mu, double lambda, MersenneTwisterFast random) {
        //create a standard normal distribution
        NormalDistribution standardNormal = new NormalDistribution(mu, lambda);
        //Step 1: Generate a uniform number and convert it to a standard normal sample using inverseCumulativeProbability
        double u1 = random.nextDouble();
        double v = standardNormal.inverseCumulativeProbability(u1);

        //step 2: compute y = v^2
        double y = v * v;

        //Step 3: Compute candidate x using the Michael-Schucany-Haas formula.
        double muSquared =mu * mu;
        double sqrtTerm = Math.sqrt(4 * mu * lambda * y + muSquared * y * y);
        double x = mu + (muSquared * y) / (2 * lambda) - (mu / (2 * lambda)) * sqrtTerm;

        //Step 4: Generate another uniform random number for the acceptance step
        double u2 = random.nextDouble();

        //Accept or reject the candidate x.
        if (u2 <= mu / (mu +x)) {
            return x;
        } else {
            return muSquared / x;
        }
    }

    /**
     * female adult: mu = 2.66, lambda = 0.33
     * @param mu
     * @param lambda
     * @param random source of randomness (pass the model's state.random for reproducible draws)
     * @return
     */
    public static double sampleInverseGaussian_SSJ (double mu, double lambda, MersenneTwisterFast random) {
        //Generate a uniform random number in (0,1)
        double u = random.nextDouble();
        //Inverse CDF sampling
        double x = InverseGaussianDist.inverseF(mu, lambda, u);
        return x;
    }


    /**
     * The 2Dt distribution is used to sample dispersal distances for male adults and male juveniles
     * male adult: a = 0.078, b = 1.47
     * male juvenile: a = 3.33, b = 2.41
     * @param a
     * @param b
     * @param random source of randomness (pass a seeded Random for reproducible draws)
     * @return
     */
    public static double sampleBivariateT(double a, double b, MersenneTwisterFast random) {
        //Generate a uniform random number in (0,1)
        double u = random.nextDouble();
        //Inverse CDF sampling
        //x = a * sqrt((1-u)^(-1/(b-1)) - 1)
        double exponent = -1.0 / (b-1);
        double x = a * Math.sqrt(Math.pow(1-u, exponent) - 1);
        return x;
    }


    /**
     * The Weibull distribution is used to sample dispersal distance for female juveniles
     * shape_b = 1.22, scale_a = 5.49
     * @param shape_b shape parameter
     * @param scale_a scale parameter
     * @param random  source of randomness (pass a seeded Random for reproducible draws)
     * @return
     */
    public static double sampleWeibull(double shape_b, double scale_a, MersenneTwisterFast random) {
        //Inverse-CDF sampling — identical to Commons Math's WeibullDistribution.sample():
        //x = scale * (-ln(1 - u))^(1/shape)
        double u = random.nextDouble();
        double x = scale_a * Math.pow(-Math.log(1 - u), 1.0 / shape_b);
        return x;
    }

}
