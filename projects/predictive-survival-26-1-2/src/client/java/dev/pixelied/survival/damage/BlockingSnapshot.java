package dev.pixelied.survival.damage;

import java.util.Objects;
import java.util.Optional;

public record BlockingSnapshot(
    boolean usingBlockingItem,
    float blockedFraction,
    int elapsedUseTicks,
    int requiredUseTicks,
    Optional<BlockingProfileSnapshot> profile,
    int cooldownTicks
) {
    public BlockingSnapshot(boolean usingBlockingItem, float blockedFraction, int elapsedUseTicks, int requiredUseTicks) {
        this(usingBlockingItem, blockedFraction, elapsedUseTicks, requiredUseTicks, Optional.empty(), 0);
    }

    public BlockingSnapshot {
        if (blockedFraction < 0f || blockedFraction > 1f || Float.isNaN(blockedFraction)) {
            throw new IllegalArgumentException("blockedFraction must be between 0 and 1");
        }
        if (elapsedUseTicks < 0 || requiredUseTicks < 0 || cooldownTicks < 0) {
            throw new IllegalArgumentException("use/cooldown ticks must be non-negative");
        }
        profile = Objects.requireNonNull(profile, "profile");
    }

    public static BlockingSnapshot none() {
        return new BlockingSnapshot(false, 0f, 0, 0, Optional.empty(), 0);
    }

    public boolean active() {
        return usingBlockingItem && cooldownTicks == 0 && elapsedUseTicks >= requiredUseTicks
            && profile.map(p -> p.remainingDurability() > 0).orElse(true);
    }

    public BlockingSnapshot withProfile(BlockingProfileSnapshot next) {
        return new BlockingSnapshot(
            usingBlockingItem && next.remainingDurability() > 0,
            blockedFraction, elapsedUseTicks, requiredUseTicks, Optional.of(next), cooldownTicks
        );
    }

    public BlockingSnapshot withElapsedUseTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be non-negative");
        return new BlockingSnapshot(usingBlockingItem, blockedFraction, ticks, requiredUseTicks, profile, cooldownTicks);
    }

    public BlockingSnapshot disableForTicks(int ticks) {
        if (ticks <= 0) return this;
        return new BlockingSnapshot(false, blockedFraction, 0, requiredUseTicks, profile, Math.max(cooldownTicks, ticks));
    }

    public BlockingSnapshot age(int ticks) {
        if (ticks <= 0 || cooldownTicks == 0) return this;
        return new BlockingSnapshot(usingBlockingItem, blockedFraction, elapsedUseTicks, requiredUseTicks,
            profile, Math.max(0, cooldownTicks - ticks));
    }
}
