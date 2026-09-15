package lbvi;

import sim.engine.SimState;
import sim.engine.Steppable;

public class   LBVITimer implements Steppable {

    @Override
    public void step(SimState simState) {
        LBVIEnvironment eState = (LBVIEnvironment) simState; //downcast to the LBVI environment
        // Advance the canonical simulation clock once per step, before the observer and agents run, so
        // every read of eState.currentYear / eState.currentJulianDay this step sees the current value.
        eState.updateYear();
        eState.updateJulianDay();
    }
}
