package dev.adrien.crystaloptimizer.sim.model;

public record BlockingState(boolean active) {
    public static BlockingState none() {
        return new BlockingState(false);
    }
}
