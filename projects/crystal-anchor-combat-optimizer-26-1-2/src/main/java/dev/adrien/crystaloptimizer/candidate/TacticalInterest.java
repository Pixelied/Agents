package dev.adrien.crystaloptimizer.candidate;

public enum TacticalInterest {
    NONE(0),
    DAMAGE_SHAPING(1),
    DAMAGE_STAIRCASE(2),
    ARMOR_BREAK(2),
    TERRAIN_CLEAR(2),
    ZERO_FEEDBACK_FINISHER(3);

    private final int priority;

    TacticalInterest(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
