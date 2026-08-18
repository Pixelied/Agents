package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MeleePredictor implements ThreatPredictor {
    private static final long POTENTIAL_ATTACK_WINDOW_TICKS = 2L;

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> result = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!Boolean.parseBoolean(entity.properties().getOrDefault("melee_capable", "false"))) continue;
            if ("false".equalsIgnoreCase(entity.properties().getOrDefault("line_of_sight", "unknown"))) continue;
            buildThreat(context, entity).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private Optional<ThreatEvent> buildThreat(PredictionContext context, WorldSnapshot.EntitySnapshot attacker) {
        Map<String, String> properties = attacker.properties();
        double reach = parseFiniteNonNegative(properties.get("attack_range"), Double.POSITIVE_INFINITY);
        if (aabbDistance(attacker.boundingBox(), context.player().boundingBox()) > reach) return Optional.empty();

        String weaponKey = properties.getOrDefault("weapon_key", "minecraft:air");
        boolean spear = isSpear(weaponKey, properties);
        WeaponSnapshot weapon = spear ? null : weaponSnapshot(properties);
        DamageRange damage;
        boolean maceSmash = false;

        if (spear) {
            Optional<DamageRange> spearDamage = spearDamage(attacker, properties);
            if (spearDamage.isEmpty()) return Optional.empty();
            damage = spearDamage.get();
        } else {
            damage = weapon.rawDamageRange();
            if (damage.max() <= 0f) return Optional.empty();
            maceSmash = weapon.isMaceSmash();
        }

        EnumSet<DamageFlag> flags = EnumSet.noneOf(DamageFlag.class);
        if (maceSmash) flags.add(DamageFlag.IS_MACE_SMASH);
        if (Boolean.parseBoolean(properties.getOrDefault("bypasses_shield", "false"))) {
            flags.add(DamageFlag.BYPASSES_SHIELD);
        }

        String defaultSource = spear
            ? "minecraft:spear"
            : maceSmash ? "minecraft:mace_smash" : defaultMeleeSource(attacker);
        String sourceKey = properties.getOrDefault("source_key", defaultSource);
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            damage,
            flags,
            Boolean.parseBoolean(properties.getOrDefault("scales_with_difficulty", "false")),
            1f,
            false,
            Optional.of(attacker.position()),
            sourceKey
        );

        boolean committed = Boolean.parseBoolean(properties.getOrDefault("attack_committed", "false"));
        TickWindow impact = committed
            ? committedWindow(properties)
            : new TickWindow(0L, POTENTIAL_ATTACK_WINDOW_TICKS);
        Confidence confidence = committed ? Confidence.MATCHED : Confidence.POTENTIAL;
        boolean canDisableBlocking = Boolean.parseBoolean(properties.getOrDefault("can_disable_blocking", "false"));
        if (!spear) canDisableBlocking = weapon.canDisableBlocking();

        return Optional.of(new ThreatEvent(
            (spear ? "spear:" : "melee:") + attacker.id(),
            ThreatKind.MELEE,
            impact,
            source,
            confidence,
            Optional.of(attacker.position()),
            Optional.of(context.player().position()),
            true,
            !source.has(DamageFlag.BYPASSES_SHIELD),
            true,
            canDisableBlocking
        ));
    }

    private static boolean isSpear(String weaponKey, Map<String, String> properties) {
        return weaponKey.endsWith("_spear")
            || "minecraft:spear".equals(weaponKey)
            || Boolean.parseBoolean(properties.getOrDefault("spear_kinetic", "false"));
    }

    private static Optional<DamageRange> spearDamage(
        WorldSnapshot.EntitySnapshot attacker,
        Map<String, String> properties
    ) {
        Float baseMobDamage = parseFiniteFloat(properties.get("spear_base_mob_damage"));
        Float damageMultiplier = parseFiniteFloat(properties.get("spear_damage_multiplier"));
        Integer maxDurationTicks = parseNonNegativeInt(properties.get("spear_damage_max_use_ticks"));
        Float minSpeed = parseFiniteFloat(properties.get("spear_damage_min_speed"));
        Float minRelativeSpeed = parseFiniteFloat(properties.get("spear_damage_min_relative_speed"));
        Integer ticksUsed = parseNonNegativeInt(properties.get("spear_ticks_used"));
        Double attackerProjection = parseFiniteDouble(properties.get("spear_attacker_speed_projection"));
        Double targetProjection = parseFiniteDouble(properties.get("spear_target_speed_projection"));

        if (baseMobDamage == null || baseMobDamage < 0f
            || damageMultiplier == null || damageMultiplier < 0f
            || maxDurationTicks == null
            || minSpeed == null || minSpeed < 0f
            || minRelativeSpeed == null || minRelativeSpeed < 0f
            || ticksUsed == null
            || attackerProjection == null
            || targetProjection == null) {
            // A visible spear with incomplete kinetic state is not safe to downgrade to normal melee math.
            return Optional.of(new DamageRange(0f, Float.MAX_VALUE));
        }

        float actionFactor = "minecraft:player".equals(attacker.typeKey()) ? 1f : 0.2f;
        return new SpearSnapshot(
            baseMobDamage,
            damageMultiplier,
            maxDurationTicks,
            minSpeed,
            minRelativeSpeed,
            ticksUsed,
            attackerProjection,
            targetProjection,
            actionFactor
        ).rawDamage();
    }

    private static WeaponSnapshot weaponSnapshot(Map<String, String> properties) {
        String weaponKey = properties.getOrDefault("weapon_key", "minecraft:air");
        DamageRange attackDamage = parseRange(properties, "attack_damage");
        DamageRange attackStrength = parseStrengthRange(properties);
        DamageRange enchantmentBonus = parseOptionalRange(properties, "enchantment_bonus", DamageRange.exact(0f));
        DamageRange fallDistance = parseOptionalRange(properties, "fall_distance", DamageRange.exact(0f));

        String critical = properties.getOrDefault("critical_possible", "unknown");
        boolean criticalConfirmed = Boolean.parseBoolean(properties.getOrDefault("critical_confirmed", "false"));
        boolean criticalPossible = criticalConfirmed || !"false".equalsIgnoreCase(critical);
        boolean canDisableBlocking = Boolean.parseBoolean(properties.getOrDefault("can_disable_blocking", "false"));

        return new WeaponSnapshot(
            weaponKey,
            attackDamage,
            attackStrength,
            enchantmentBonus,
            fallDistance,
            criticalPossible,
            criticalConfirmed,
            canDisableBlocking
        );
    }

    private static TickWindow committedWindow(Map<String, String> properties) {
        long earliest = parseNonNegativeLong(properties.get("attack_earliest_tick"), 0L);
        long latest = parseNonNegativeLong(properties.get("attack_latest_tick"), earliest);
        if (latest < earliest) latest = earliest;
        return new TickWindow(earliest, latest);
    }

    private static DamageRange parseRange(Map<String, String> properties, String key) {
        Float exact = parseFiniteFloat(properties.get(key));
        if (exact != null && exact >= 0f) return DamageRange.exact(exact);
        Float min = parseFiniteFloat(properties.get(key + "_min"));
        Float max = parseFiniteFloat(properties.get(key + "_max"));
        if (min != null && max != null && min >= 0f && max >= min) return new DamageRange(min, max);
        return new DamageRange(0f, Float.MAX_VALUE);
    }

    private static DamageRange parseOptionalRange(
        Map<String, String> properties,
        String key,
        DamageRange fallback
    ) {
        Float exact = parseFiniteFloat(properties.get(key));
        if (exact != null && exact >= 0f) return DamageRange.exact(exact);
        Float min = parseFiniteFloat(properties.get(key + "_min"));
        Float max = parseFiniteFloat(properties.get(key + "_max"));
        if (min != null && max != null && min >= 0f && max >= min) return new DamageRange(min, max);
        return fallback;
    }

    private static DamageRange parseStrengthRange(Map<String, String> properties) {
        Float exact = parseFiniteFloat(properties.get("attack_strength"));
        if (exact != null) {
            float clamped = clamp01(exact);
            return DamageRange.exact(clamped);
        }
        Float min = parseFiniteFloat(properties.get("attack_strength_min"));
        Float max = parseFiniteFloat(properties.get("attack_strength_max"));
        if (min != null && max != null && max >= min) {
            return new DamageRange(clamp01(min), clamp01(max));
        }
        return new DamageRange(0f, 1f);
    }

    private static String defaultMeleeSource(WorldSnapshot.EntitySnapshot attacker) {
        return "minecraft:player".equals(attacker.typeKey())
            ? "minecraft:player_attack"
            : "minecraft:mob_attack";
    }

    private static double aabbDistance(dev.pixelied.survival.core.AabbSnapshot a, dev.pixelied.survival.core.AabbSnapshot b) {
        double dx = axisGap(a.minX(), a.maxX(), b.minX(), b.maxX());
        double dy = axisGap(a.minY(), a.maxY(), b.minY(), b.maxY());
        double dz = axisGap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisGap(double minA, double maxA, double minB, double maxB) {
        if (maxA < minB) return minB - maxA;
        if (maxB < minA) return minA - maxB;
        return 0d;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 1f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static Float parseFiniteFloat(String value) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parseFiniteDouble(String value) {
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseNonNegativeInt(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double parseFiniteNonNegative(String value, double fallback) {
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0d ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseNonNegativeLong(String value, long fallback) {
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0L ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
