package dev.adrien.crystaloptimizer.sim.model;

public record HurtWindowState(int invulnerableTime, float lastHurt) {
    public HurtWindowState tick(int ticks) {
        return new HurtWindowState(Math.max(0, invulnerableTime - ticks), lastHurt);
    }
}
