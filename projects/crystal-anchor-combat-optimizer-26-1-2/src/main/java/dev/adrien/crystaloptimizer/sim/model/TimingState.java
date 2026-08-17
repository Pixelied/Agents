package dev.adrien.crystaloptimizer.sim.model;

public record TimingState(
    long estimatedServerTick,
    double confidence,
    double roundTripMillis,
    double jitterMillis
) {
    public TimingState {
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        if (!Double.isFinite(roundTripMillis) || roundTripMillis < 0.0) {
            throw new IllegalArgumentException("roundTripMillis must be non-negative and finite");
        }
        if (!Double.isFinite(jitterMillis) || jitterMillis < 0.0) {
            throw new IllegalArgumentException("jitterMillis must be non-negative and finite");
        }
    }

    public static TimingState unknown() {
        return new TimingState(-1L, 0.0, 0.0, 0.0);
    }
}
