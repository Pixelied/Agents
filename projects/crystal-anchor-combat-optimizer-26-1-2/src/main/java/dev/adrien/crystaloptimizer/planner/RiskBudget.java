package dev.adrien.crystaloptimizer.planner;

public record RiskBudget(Mode mode) {
    public enum Mode {
        SAFE,
        ADAPTIVE,
        RUTHLESS
    }

    public static RiskBudget safe() {
        return new RiskBudget(Mode.SAFE);
    }

    public static RiskBudget adaptive() {
        return new RiskBudget(Mode.ADAPTIVE);
    }

    public static RiskBudget ruthless() {
        return new RiskBudget(Mode.RUTHLESS);
    }

    public double maxAcceptableSelfRisk(double threat, double targetDeathProbability) {
        double boundedThreat = clamp01(threat);
        double boundedDeath = clamp01(targetDeathProbability);
        return switch (mode) {
            case SAFE -> 0.08;
            case ADAPTIVE -> Math.min(0.82, 0.15 + 0.45 * boundedThreat + 0.15 * boundedDeath);
            case RUTHLESS -> 0.90;
        };
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("risk inputs must be finite");
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
