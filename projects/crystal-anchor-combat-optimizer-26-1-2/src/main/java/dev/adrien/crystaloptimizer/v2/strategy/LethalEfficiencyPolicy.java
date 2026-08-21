package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import java.util.Objects;

public final class LethalEfficiencyPolicy {
    private static final float NORMAL_PRESSURE_FLOOR = 6.0f;
    private static final float FACE_PLACE_FLOOR = 2.0f;
    private static final float EXISTING_CRYSTAL_BREAK_FLOOR = 1.0f;

    public enum Reason {
        ALLOWED,
        SELF_LETHAL,
        SELF_TOTEM_POP,
        SELF_DAMAGE_LIMIT,
        BAD_TRADE,
        LOW_VALUE_SPEND
    }

    public record Decision(boolean allowed, Reason reason) {
        public Decision {
            Objects.requireNonNull(reason, "reason");
            if (allowed != (reason == Reason.ALLOWED)) {
                throw new IllegalArgumentException("allowed must match ALLOWED reason");
            }
        }

        public static Decision allow() {
            return new Decision(true, Reason.ALLOWED);
        }

        public static Decision reject(Reason reason) {
            if (reason == Reason.ALLOWED) {
                throw new IllegalArgumentException("rejection reason cannot be ALLOWED");
            }
            return new Decision(false, reason);
        }
    }

    public static Decision evaluate(
        SelfDamageEstimate self,
        OpportunityIntent intent,
        float usefulExpectedTargetDamage,
        boolean directExistingCrystalBreak,
        float targetEffectiveHealth,
        OptimizerConfig config
    ) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(config, "config");
        if (!Float.isFinite(usefulExpectedTargetDamage) || usefulExpectedTargetDamage < 0.0f) {
            throw new IllegalArgumentException("usefulExpectedTargetDamage must be finite and non-negative");
        }
        if (!Float.isFinite(targetEffectiveHealth) || targetEffectiveHealth < 0.0f) {
            throw new IllegalArgumentException("targetEffectiveHealth must be finite and non-negative");
        }

        SelfSurvivalPolicy.Decision survival = SelfSurvivalPolicy.evaluate(
            self,
            intent,
            usefulExpectedTargetDamage,
            config
        );
        if (!survival.allowed()) {
            return Decision.reject(switch (survival.reason()) {
                case SELF_LETHAL -> Reason.SELF_LETHAL;
                case SELF_TOTEM_POP -> Reason.SELF_TOTEM_POP;
                case SELF_DAMAGE_LIMIT -> Reason.SELF_DAMAGE_LIMIT;
                case BAD_TRADE -> Reason.BAD_TRADE;
                case ALLOWED -> throw new IllegalStateException("allowed survival decision cannot reject");
            });
        }

        if (intent == OpportunityIntent.LETHAL
            || intent == OpportunityIntent.POP
            || intent == OpportunityIntent.STAIRCASE) {
            return Decision.allow();
        }

        float floor = directExistingCrystalBreak
            ? EXISTING_CRYSTAL_BREAK_FLOOR
            : targetEffectiveHealth <= config.facePlaceHealth()
                ? FACE_PLACE_FLOOR
                : Math.max(config.minDamage(), NORMAL_PRESSURE_FLOOR);
        return usefulExpectedTargetDamage >= floor
            ? Decision.allow()
            : Decision.reject(Reason.LOW_VALUE_SPEND);
    }

    private LethalEfficiencyPolicy() {
    }
}
