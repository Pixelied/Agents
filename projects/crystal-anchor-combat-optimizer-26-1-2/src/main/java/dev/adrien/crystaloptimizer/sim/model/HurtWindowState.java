package dev.adrien.crystaloptimizer.sim.model;

public record HurtWindowState(int invulnerableTime, float lastHurt, boolean lastHurtKnown) {
    public HurtWindowState(int invulnerableTime, float lastHurt) {
        this(invulnerableTime, lastHurt, true);
    }

    public static HurtWindowState unknownThreshold(int invulnerableTime) {
        return new HurtWindowState(invulnerableTime, 0.0f, false);
    }

    public HurtWindowState tick(int ticks) {
        return new HurtWindowState(Math.max(0, invulnerableTime - ticks), lastHurt, lastHurtKnown);
    }
}
