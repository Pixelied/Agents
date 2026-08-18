package dev.adrien.crystaloptimizer.sim.damage;

public enum ExplosionKind {
    CRYSTAL(6.0f),
    ANCHOR(5.0f);

    private final float radius;

    ExplosionKind(float radius) {
        this.radius = radius;
    }

    public float radius() {
        return radius;
    }
}
