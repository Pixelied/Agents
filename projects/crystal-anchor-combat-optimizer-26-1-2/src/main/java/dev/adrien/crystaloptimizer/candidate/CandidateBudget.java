package dev.adrien.crystaloptimizer.candidate;

public record CandidateBudget(
    int crystalAttacks,
    int crystalPlacements,
    int anchorDetonations,
    int anchorSetup,
    int supportObsidian,
    int waits
) {
    public CandidateBudget {
        if (crystalAttacks < 0 || crystalPlacements < 0 || anchorDetonations < 0
            || anchorSetup < 0 || supportObsidian < 0 || waits < 0) {
            throw new IllegalArgumentException("candidate quotas must be non-negative");
        }
    }

    public int quota(CandidateCategory category) {
        return switch (category) {
            case CRYSTAL_ATTACK -> crystalAttacks;
            case CRYSTAL_PLACEMENT -> crystalPlacements;
            case ANCHOR_DETONATION -> anchorDetonations;
            case ANCHOR_SETUP -> anchorSetup;
            case SUPPORT_OBSIDIAN -> supportObsidian;
            case WAIT -> waits;
        };
    }
}
