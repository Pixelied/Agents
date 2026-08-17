package dev.adrien.crystaloptimizer.sim.model;

public record AnchorState(int charges) {
    public AnchorState {
        if (charges < 0 || charges > 4) {
            throw new IllegalArgumentException("Respawn anchor charges must be in [0, 4]");
        }
    }

    public boolean charged() {
        return charges > 0;
    }
}
