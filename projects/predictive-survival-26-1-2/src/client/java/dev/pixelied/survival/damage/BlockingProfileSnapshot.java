package dev.pixelied.survival.damage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable client-observable BLOCKS_ATTACKS semantics for a blocking item. */
public record BlockingProfileSnapshot(
    List<DamageReduction> damageReductions,
    ItemDamageFunction itemDamage,
    Set<String> bypassedDamageTypeKeys,
    float disableCooldownScale,
    int remainingDurability
) {
    public BlockingProfileSnapshot {
        damageReductions = List.copyOf(Objects.requireNonNull(damageReductions, "damageReductions"));
        itemDamage = Objects.requireNonNull(itemDamage, "itemDamage");
        bypassedDamageTypeKeys = Set.copyOf(Objects.requireNonNull(bypassedDamageTypeKeys, "bypassedDamageTypeKeys"));
        if (!Float.isFinite(disableCooldownScale) || disableCooldownScale < 0f) {
            throw new IllegalArgumentException("disableCooldownScale must be finite and non-negative");
        }
        if (remainingDurability < 0) throw new IllegalArgumentException("remainingDurability must be non-negative");
    }

    public static BlockingProfileSnapshot fullBlock(int remainingDurability) {
        return new BlockingProfileSnapshot(
            List.of(new DamageReduction(90f, Optional.empty(), 0f, 1f)),
            new ItemDamageFunction(3f, 1f, 1f),
            Set.of(), 1f, remainingDurability
        );
    }

    public float resolveBlockedDamage(DamageSourceSnapshot source, float dealtDamage, double horizontalAngleRadians) {
        if (dealtDamage <= 0f || remainingDurability <= 0 || bypassedDamageTypeKeys.contains(source.sourceKey())) return 0f;
        float blocked = 0f;
        for (DamageReduction reduction : damageReductions) {
            blocked = saturatingAdd(blocked, reduction.resolve(source, dealtDamage, horizontalAngleRadians));
        }
        return clamp(blocked, 0f, dealtDamage);
    }

    public BlockingProfileSnapshot damageForBlockedAmount(float blockedDamage) {
        if (remainingDurability <= 0 || blockedDamage <= 0f) return this;
        int damage = itemDamage.apply(blockedDamage);
        if (damage <= 0) return this;
        return new BlockingProfileSnapshot(
            damageReductions, itemDamage, bypassedDamageTypeKeys, disableCooldownScale,
            Math.max(0, remainingDurability - damage)
        );
    }

    public int disableTicks(float attackerBaseSeconds) {
        if (!Float.isFinite(attackerBaseSeconds) || attackerBaseSeconds <= 0f || disableCooldownScale <= 0f) return 0;
        double seconds = (double) attackerBaseSeconds * disableCooldownScale;
        if (!Double.isFinite(seconds) || seconds * 20d >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(0, Math.round((float) seconds * 20f));
    }

    public record DamageReduction(
        float horizontalBlockingAngleDegrees,
        Optional<Set<String>> damageTypeKeys,
        float base,
        float factor
    ) {
        public DamageReduction {
            if (!Float.isFinite(horizontalBlockingAngleDegrees) || horizontalBlockingAngleDegrees <= 0f) {
                throw new IllegalArgumentException("horizontalBlockingAngleDegrees must be finite and positive");
            }
            damageTypeKeys = Objects.requireNonNull(damageTypeKeys, "damageTypeKeys")
                .map(keys -> Set.copyOf(Objects.requireNonNull(keys, "damageTypeKeys value")));
            if (!Float.isFinite(base) || !Float.isFinite(factor)) {
                throw new IllegalArgumentException("base/factor must be finite");
            }
        }

        float resolve(DamageSourceSnapshot source, float dealtDamage, double angleRadians) {
            double maxAngle = Math.toRadians(horizontalBlockingAngleDegrees);
            if (!Double.isFinite(angleRadians) || angleRadians > maxAngle) return 0f;
            if (damageTypeKeys.isPresent() && !damageTypeKeys.get().contains(source.sourceKey())) return 0f;
            return clamp(base + factor * dealtDamage, 0f, dealtDamage);
        }
    }

    public record ItemDamageFunction(float threshold, float base, float factor) {
        public ItemDamageFunction {
            if (!Float.isFinite(threshold) || threshold < 0f || !Float.isFinite(base) || !Float.isFinite(factor)) {
                throw new IllegalArgumentException("item damage function values must be finite; threshold must be non-negative");
            }
        }

        int apply(float dealtDamage) {
            if (!Float.isFinite(dealtDamage) || dealtDamage < threshold) return 0;
            double result = Math.floor((double) base + factor * dealtDamage);
            if (result <= 0d) return 0;
            if (result >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            return (int) result;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float saturatingAdd(float left, float right) {
        double result = (double) left + right;
        return !Double.isFinite(result) || result >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) result;
    }
}
