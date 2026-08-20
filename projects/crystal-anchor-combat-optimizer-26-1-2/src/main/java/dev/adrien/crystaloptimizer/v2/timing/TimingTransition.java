package dev.adrien.crystaloptimizer.v2.timing;

public enum TimingTransition {
    IMMEDIATE(false),
    BLOCK_INTERACTION_TO_ACK(true),
    CRYSTAL_PLACE_TO_SPAWN(true),
    CRYSTAL_ATTACK_TO_REMOVAL(true),
    TOTEM_POP_TO_VISIBLE_REFILL(true),
    SERVER_UPDATE_CADENCE(false);

    private final boolean hardFeedback;

    TimingTransition(boolean hardFeedback) {
        this.hardFeedback = hardFeedback;
    }

    public boolean hardFeedback() {
        return hardFeedback;
    }
}
