package dev.adrien.crystaloptimizer.sim.model;

public enum TotemState {
    NONE,
    MAINHAND,
    OFFHAND,
    BOTH;

    public boolean available() {
        return this != NONE;
    }

    public TotemState consumeFirst() {
        return switch (this) {
            case NONE -> NONE;
            case MAINHAND, OFFHAND -> NONE;
            case BOTH -> OFFHAND;
        };
    }
}
