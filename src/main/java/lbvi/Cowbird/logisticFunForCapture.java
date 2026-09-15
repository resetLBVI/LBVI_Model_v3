package lbvi.Cowbird;

import lbvi.LBVIEnvironment;
import lbvi.Traps.TrapInfoIndentifier;
import lbvi.VegInfoIdentifier;

import java.util.ArrayList;
import java.util.List;

public class logisticFunForCapture {

    /**
     * Computes the distance-adjusted capture probability using a logistic function
     * anchored at two (distance, probability) points supplied by the environment:
     *   - At d = mpCowbirdCaptureP90DistKM:  P = 0.90  (near trap, high probability)
     *   - At d = mpCowbirdCaptureP10DistKM:  P = 0.10  (far from trap, low probability)
     *
     * The 0.90 / 0.10 target probabilities are fixed; the two parameters supply the
     * DISTANCES (in km) at which those probabilities occur.
     *
     * Standard logistic form:  P(d) = 1 / (1 + exp(-(a + b*d)))
     * Parameters a and b are solved via the logit transformation of the two anchors.
     *
     * @param distanceKM  distance from cowbird to trap (kilometers)
     * @param state       the simulation environment (provides mpCowbirdCaptureP90DistKM,
     *                    mpCowbirdCaptureP10DistKM)
     * @return            distance-adjusted capture probability at the given distance
     */
    public static double computeCaptureProb(LBVIEnvironment state, double distanceKM) {
        double p90Dist = state.mpCowbirdCaptureP90DistKM;  // km at which P = 0.90
        double p10Dist = state.mpCowbirdCaptureP10DistKM;  // km at which P = 0.10
        final double logitHigh = Math.log(0.9 / 0.1);      // +2.19722  (logit of 0.90)
        final double logitLow  = Math.log(0.1 / 0.9);      // -2.19722  (logit of 0.10)
        // slope b is negative (probability decreases with distance)
        double b = (logitLow - logitHigh) / (p10Dist - p90Dist);
        double a = logitHigh - b * p90Dist;
        return 1.0 / (1.0 + Math.exp(-(a + b * distanceKM)));
    }

    /**
     * Returns all traps that are open on the current simulation day and year.
     * A trap is open when its trapYear matches the current year and
     * currentJulianDay falls within [trapOpenDate, trapCloseDate].
     *
     * @param state the simulation environment (provides trapInfo, currentYear, currentJulianDay)
     * @return list of currently open traps
     */
    public static List<TrapInfoIndentifier> findOpenTraps(LBVIEnvironment state) {
        List<TrapInfoIndentifier> openTraps = new ArrayList<>();
        for (TrapInfoIndentifier trap : state.trapInfo.values()) {
            if (trap.year == state.currentYear &&
                state.currentJulianDay >= trap.trapOpenDate &&
                state.currentJulianDay <= trap.trapCloseDate) {
                openTraps.add(trap);
            }
        }
        return openTraps;
    }

    /**
     * Determines whether a newly arrived cowbird is captured by any eligible trap.
     * The cowbird's location is resolved from nestTerrID via state.vegInfo, replacing
     * the need for a separate findNearbyCowbirdTraps() call.
     * For each trap within mpMaxCowbirdCaptureDistM, a separate random draw is made
     * against the distance-adjusted capture probability. If any draw succeeds, the
     * cowbird is captured.
     *
     * @param state       the simulation environment
     * @param nestTerrID  territory ID of the nest (and cowbird) location
     * @param openTraps      list of active traps to check
     * @return            true if the cowbird is captured by any eligible trap, false otherwise
     */
    public static boolean isCaptured(LBVIEnvironment state, int nestTerrID, List<TrapInfoIndentifier> openTraps) {
        VegInfoIdentifier currentTerritory = state.vegTerrInfo.get(nestTerrID);
        if (currentTerritory == null) {
            System.err.println("isCaptured: nestTerrID " + nestTerrID + " not found in vegInfo");
            return false;
        }
        double cowbirdX = currentTerritory.terrCoordX;
        double cowbirdY = currentTerritory.terrCoordY;
        for (TrapInfoIndentifier trap : openTraps) {
            double dx = cowbirdX - trap.trapPOINT_X;
            double dy = cowbirdY - trap.trapPOINT_Y;
            double distanceM = Math.sqrt(dx * dx + dy * dy);
            if (distanceM > state.mpMaxCowbirdCaptureDistM) continue; // trap not eligible (meters)
            double captureProb = computeCaptureProb(state, distanceM / 1000.0); // curve expects km
            if (state.random.nextDouble() < captureProb) {
                return true;
            }
        }
        return false;
    }
}
