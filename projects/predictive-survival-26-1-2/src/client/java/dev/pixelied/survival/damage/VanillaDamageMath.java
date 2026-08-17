package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.DifficultySnapshot;

public final class VanillaDamageMath {
    private VanillaDamageMath() {
    }

    public static float scaleForDifficulty(float damage, DifficultySnapshot difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0f;
            case EASY -> Math.min(damage / 2f + 1f, damage);
            case NORMAL -> damage;
            case HARD -> damage * 1.5f;
        };
    }

    public static float sanitize(float damage) {
        return Float.isFinite(damage) ? damage : Float.MAX_VALUE;
    }
}
