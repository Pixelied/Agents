package dev.adrien.crystaloptimizer.reconcile;

public enum FailureKind {
    LEGALITY_FAILURE,
    STATE_RACE,
    TARGET_DIVERGENCE,
    NETWORK_UNCERTAINTY,
    RESOURCE_FAILURE,
    SIMULATION_MISMATCH
}
