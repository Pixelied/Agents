package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

public final class TargetManager {
    private static final int SHORTLIST_LIMIT = 3;

    private UUID stickyTarget;

    public Optional<AbstractClientPlayer> select(
        LocalPlayer self,
        ClientLevel level,
        OptimizerConfig config,
        ToDoubleFunction<AbstractClientPlayer> immediateLethalMillis
    ) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(immediateLethalMillis, "immediateLethalMillis");

        double rangeSquared = config.targetRange() * config.targetRange();
        List<AbstractClientPlayer> shortlist = level.players().stream()
            .filter(target -> valid(self, target, rangeSquared))
            .sorted(Comparator
                .comparingInt((AbstractClientPlayer target) -> shortlistPriority(self, target)).reversed()
                .thenComparingDouble(self::distanceToSqr))
            .limit(SHORTLIST_LIMIT)
            .toList();

        if (shortlist.isEmpty()) {
            stickyTarget = null;
            return Optional.empty();
        }

        AbstractClientPlayer selected = shortlist.stream()
            .min(Comparator
                .comparingDouble((AbstractClientPlayer target) -> safeScore(immediateLethalMillis, target))
                .thenComparingInt(target -> target.getUUID().equals(stickyTarget) ? 0 : 1)
                .thenComparingInt(target -> recentAttacker(self, target) ? 0 : 1)
                .thenComparingDouble(self::distanceToSqr))
            .orElseThrow();
        stickyTarget = selected.getUUID();
        return Optional.of(selected);
    }

    public Optional<UUID> stickyTarget() {
        return Optional.ofNullable(stickyTarget);
    }

    public void clear() {
        stickyTarget = null;
    }

    private int shortlistPriority(LocalPlayer self, AbstractClientPlayer target) {
        int priority = 0;
        if (target.getUUID().equals(stickyTarget)) {
            priority += 2;
        }
        if (recentAttacker(self, target)) {
            priority += 1;
        }
        return priority;
    }

    private static boolean valid(LocalPlayer self, AbstractClientPlayer target, double rangeSquared) {
        if (target == self || target.isRemoved() || target.isDeadOrDying() || target.isSpectator()) {
            return false;
        }
        if (self.isAlliedTo(target)) {
            return false;
        }
        return self.distanceToSqr(target) <= rangeSquared;
    }

    private static boolean recentAttacker(LocalPlayer self, AbstractClientPlayer target) {
        return self.getLastHurtByMob() == target
            && self.tickCount - self.getLastHurtByMobTimestamp() <= 40;
    }

    private static double safeScore(
        ToDoubleFunction<AbstractClientPlayer> immediateLethalMillis,
        AbstractClientPlayer target
    ) {
        double score = immediateLethalMillis.applyAsDouble(target);
        return Double.isFinite(score) && score >= 0.0 ? score : Double.POSITIVE_INFINITY;
    }
}
