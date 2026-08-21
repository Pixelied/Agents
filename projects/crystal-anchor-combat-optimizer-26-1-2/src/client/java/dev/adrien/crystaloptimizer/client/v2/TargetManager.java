package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.strategy.HurtThresholdEstimate;
import dev.adrien.crystaloptimizer.v2.strategy.StrategicTargetSelector;
import dev.adrien.crystaloptimizer.v2.strategy.TargetPreScore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class TargetManager {
    private UUID stickyTarget;

    public List<TargetPreScore> preScores(
        LocalPlayer self,
        ClientLevel level,
        OptimizerConfig config,
        Set<UUID> protectedIds
    ) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(protectedIds, "protectedIds");
        double rangeSquared = config.targetRange() * config.targetRange();
        return level.players().stream()
            .filter(target -> valid(self, target, rangeSquared, protectedIds))
            .map(target -> new TargetPreScore(
                target.getUUID(),
                self.distanceToSqr(target),
                effectiveHealthUpperBound(target),
                HurtThresholdEstimate.MAX_CRYSTAL_HARD_INCOMING,
                recentAttacker(self, target),
                target.getUUID().equals(stickyTarget)
            ))
            .sorted(Comparator
                .comparing(TargetPreScore::sticky).reversed()
                .thenComparing(TargetPreScore::recentAttacker, Comparator.reverseOrder())
                .thenComparing(TargetPreScore::cheapCouldFinish, Comparator.reverseOrder())
                .thenComparingDouble(TargetPreScore::effectiveHealthUpperBound)
                .thenComparingDouble(TargetPreScore::distanceSquared))
            .limit(StrategicTargetSelector.MAX_EXACT_TARGETS)
            .toList();
    }

    public Optional<AbstractClientPlayer> select(
        LocalPlayer self,
        ClientLevel level,
        OptimizerConfig config,
        ToDoubleFunction<AbstractClientPlayer> immediateLethalMillis
    ) {
        Objects.requireNonNull(immediateLethalMillis, "immediateLethalMillis");
        Map<UUID, AbstractClientPlayer> byId = level.players().stream()
            .collect(Collectors.toMap(AbstractClientPlayer::getUUID, player -> player));
        List<AbstractClientPlayer> candidates = preScores(self, level, config, Set.of()).stream()
            .map(score -> byId.get(score.targetId()))
            .filter(Objects::nonNull)
            .toList();
        if (candidates.isEmpty()) {
            clear();
            return Optional.empty();
        }
        AbstractClientPlayer selected = candidates.stream()
            .min(Comparator
                .comparingDouble((AbstractClientPlayer target) -> safeScore(immediateLethalMillis, target))
                .thenComparingInt(target -> target.getUUID().equals(stickyTarget) ? 0 : 1)
                .thenComparingInt(target -> recentAttacker(self, target) ? 0 : 1)
                .thenComparingDouble(self::distanceToSqr))
            .orElseThrow();
        markSelected(selected.getUUID());
        return Optional.of(selected);
    }

    public Optional<UUID> stickyTarget() {
        return Optional.ofNullable(stickyTarget);
    }

    public void markSelected(UUID targetId) {
        stickyTarget = Objects.requireNonNull(targetId, "targetId");
    }

    public void clear() {
        stickyTarget = null;
    }

    private static boolean valid(
        LocalPlayer self,
        AbstractClientPlayer target,
        double rangeSquared,
        Set<UUID> protectedIds
    ) {
        if (target == self || target.isRemoved() || target.isDeadOrDying() || target.isSpectator()) {
            return false;
        }
        if (protectedIds.contains(target.getUUID()) || self.isAlliedTo(target)) {
            return false;
        }
        return self.distanceToSqr(target) <= rangeSquared;
    }

    private static boolean recentAttacker(LocalPlayer self, AbstractClientPlayer target) {
        return self.getLastHurtByMob() == target
            && self.tickCount - self.getLastHurtByMobTimestamp() <= 40;
    }

    private static float effectiveHealthUpperBound(AbstractClientPlayer target) {
        MobEffectInstance absorption = target.getEffect(MobEffects.ABSORPTION);
        float absorptionUpper = absorption == null
            ? 0.0f
            : 4.0f * Math.max(0, absorption.getAmplifier() + 1);
        return Math.max(0.0f, target.getHealth()) + absorptionUpper;
    }

    private static double safeScore(
        ToDoubleFunction<AbstractClientPlayer> immediateLethalMillis,
        AbstractClientPlayer target
    ) {
        double score = immediateLethalMillis.applyAsDouble(target);
        return Double.isFinite(score) && score >= 0.0 ? score : Double.POSITIVE_INFINITY;
    }
}
