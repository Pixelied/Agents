package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import java.util.Objects;

public final class SelfSurvivalPolicy {
    private static final float MIN_REMAINING_HEALTH = 0.5f;
    private static final float MIN_PRESSURE_TRADE_RATIO = 1.25f;

    public enum Reason {
        ALLOWED,
        SELF_LETHAL,
        SELF_TOTEM_POP,
        SELF_DAMAGE_LIMIT,
        BAD_TRADE
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
        float usefulTargetDamage,
        OptimizerConfig config
    ) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(config, "config");
        if (!Float.isFinite(usefulTargetDamage) || usefulTargetDamage < 0.0f) {
            throw new IllegalArgumentException("usefulTargetDamage must be finite and non-negative");
        }

        if (self.totemTriggered()) {
            return Decision.reject(Reason.SELF_TOTEM_POP);
        }
        if (self.worstCaseRemainingHealth() <= MIN_REMAINING_HEALTH) {
            return Decision.reject(Reason.SELF_LETHAL);
        }

        boolean privilegedLethalSpeed = config.strategy() == OptimizerStrategy.LETHAL_SPEED
            && (intent == OpportunityIntent.LETHAL
                || intent == OpportunityIntent.POP
                || intent == OpportunityIntent.STAIRCASE);
        if (!privilegedLethalSpeed && self.worstCaseDamage() > config.maxSelfDamage()) {
            return Decision.reject(Reason.SELF_DAMAGE_LIMIT);
        }

        if (intent == OpportunityIntent.PRESSURE && self.worstCaseDamage() > 0.0f) {
            float tradeRatio = usefulTargetDamage / self.worstCaseDamage();
            if (tradeRatio < MIN_PRESSURE_TRADE_RATIO) {
                return Decision.reject(Reason.BAD_TRADE);
            }
        }

        return Decision.allow();
    }

    private SelfSurvivalPolicy() {
    }
}
