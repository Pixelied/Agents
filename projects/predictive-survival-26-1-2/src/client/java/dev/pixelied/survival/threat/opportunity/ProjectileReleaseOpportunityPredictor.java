package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.VanillaDamageOracle;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pre-arms against player-launched projectile families whose exact-runtime authority probe proved
 * that first-projectile observation can arrive too late for server-authoritative death protection.
 * Bow requires synchronized active-use evidence; merely holding one never creates an opportunity.
 */
public final class ProjectileReleaseOpportunityPredictor implements LethalOpportunityPredictor {
    private static final String BOW = "minecraft:bow";
    private static final String CROSSBOW = "minecraft:crossbow";
    private static final String WIND_CHARGE = "minecraft:wind_charge";
    private static final String SPLASH_POTION = "minecraft:splash_potion";

    private static final int BOW_MIN_LEGAL_USE_TICKS = 3;
    private static final double ARROW_BASE_DAMAGE = 2.0d;
    private static final double CROSSBOW_ARROW_SPEED = 3.15d;
    private static final double CROSSBOW_FIREWORK_SPEED = 1.6d;
    private static final double WIND_CHARGE_SPEED = 1.5d;
    private static final double SPLASH_POTION_SPEED = 0.5d;

    private static final float CROSSBOW_ARROW_RAW_DAMAGE = 7f;
    private static final float WIND_CHARGE_RAW_DAMAGE = 1f;

    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();

    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<LethalOpportunity> result = new ArrayList<>();
        AabbSnapshot firstTickTarget = sweptOneTick(context);

        for (WorldSnapshot.EntitySnapshot attacker : context.world().entities()) {
            if (!"minecraft:player".equals(attacker.typeKey())) continue;
            if ("false".equalsIgnoreCase(attacker.properties().getOrDefault("line_of_sight", "unknown"))) continue;

            Vec3Snapshot eye = eyePosition(attacker);
            if (eye == null) continue;

            evaluateHand(context, attacker, eye, firstTickTarget, Hand.MAIN_HAND).ifPresent(result::add);
            if (result.size() >= context.limits().maxOpportunities()) break;
            evaluateHand(context, attacker, eye, firstTickTarget, Hand.OFF_HAND).ifPresent(result::add);
            if (result.size() >= context.limits().maxOpportunities()) break;
        }
        return List.copyOf(result);
    }

    private Optional<LethalOpportunity> evaluateHand(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot attacker,
        Vec3Snapshot eye,
        AabbSnapshot firstTickTarget,
        Hand hand
    ) {
        Map<String, String> properties = attacker.properties();
        String item = properties.getOrDefault(hand.itemKeyProperty, "minecraft:air");
        Release release = null;

        if (BOW.equals(item)) {
            release = bowRelease(context, properties, hand).orElse(null);
        } else if (CROSSBOW.equals(item)) {
            String projectileKind = properties.getOrDefault(hand.prefix + "crossbow_projectile_kind", "none");
            if ("arrow".equals(projectileKind)) {
                release = new Release(
                    "crossbow_arrow",
                    CROSSBOW_ARROW_SPEED,
                    CROSSBOW_ARROW_RAW_DAMAGE,
                    0L,
                    EnumSet.of(DamageFlag.IS_PROJECTILE),
                    "minecraft:arrow",
                    true,
                    Map.of("crossbow_projectile_kind", "arrow")
                );
            } else if ("firework".equals(projectileKind)) {
                Integer explosions = nonNegativeInt(properties.get(hand.prefix + "crossbow_firework_explosions"));
                if (explosions != null && explosions > 0) {
                    float raw = fireworkRawDamage(explosions);
                    release = new Release(
                        "crossbow_firework",
                        CROSSBOW_FIREWORK_SPEED,
                        raw,
                        0L,
                        EnumSet.of(DamageFlag.IS_EXPLOSION),
                        "minecraft:fireworks",
                        true,
                        Map.of(
                            "crossbow_projectile_kind", "firework",
                            "firework_explosions", Integer.toString(explosions)
                        )
                    );
                }
            }
        } else if (WIND_CHARGE.equals(item)) {
            release = new Release(
                "wind_charge",
                WIND_CHARGE_SPEED,
                WIND_CHARGE_RAW_DAMAGE,
                0L,
                EnumSet.of(DamageFlag.IS_PROJECTILE),
                "minecraft:wind_charge",
                true,
                Map.of()
            );
        } else if (SPLASH_POTION.equals(item)) {
            Float instantDamage = positiveFloat(properties.get(hand.prefix + "potion_instant_damage"));
            if (instantDamage != null) {
                release = new Release(
                    "splash_harming",
                    SPLASH_POTION_SPEED,
                    instantDamage,
                    0L,
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    "minecraft:indirect_magic",
                    false,
                    Map.of("potion_instant_damage", Float.toString(instantDamage))
                );
            }
        }

        if (release == null || !withinFirstTickReach(eye, firstTickTarget, release.speed)) {
            return Optional.empty();
        }

        long latestImpact = saturatingAdd(release.earliestImpactTicks, reactionTicks(context));
        TickWindow impact = new TickWindow(release.earliestImpactTicks, Math.max(release.earliestImpactTicks, latestImpact));
        String id = "opportunity:projectile_release:" + attacker.id() + ':' + release.family + ':' + hand.evidence;
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(release.rawDamage),
            release.flags,
            false,
            1f,
            false,
            Optional.of(eye),
            release.sourceKey
        );
        ThreatEvent projected = new ThreatEvent(
            id,
            ThreatKind.PROJECTILE,
            impact,
            damage,
            Confidence.POTENTIAL,
            Optional.of(eye),
            Optional.of(context.player().position()),
            true,
            release.blockable,
            true,
            false
        );
        if (!damageOracle.lethalWithoutDeathProtection(
            context.player(), new ThreatTimeline(List.of(projected)))) {
            return Optional.empty();
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("attacker_id", attacker.id());
        evidence.put("release_family", release.family);
        evidence.put("hand", hand.evidence);
        evidence.put("first_tick_speed", Double.toString(release.speed));
        evidence.put("first_tick_reach", "true");
        evidence.put("release_earliest_impact_ticks", Long.toString(release.earliestImpactTicks));
        evidence.putAll(release.evidence);
        return Optional.of(new LethalOpportunity(
            id,
            OpportunityFamily.PROJECTILE,
            projected,
            Confidence.POTENTIAL,
            1,
            evidence
        ));
    }

    private static Optional<Release> bowRelease(
        PredictionContext context,
        Map<String, String> properties,
        Hand hand
    ) {
        if (!Boolean.parseBoolean(properties.getOrDefault("using_item", "false"))) return Optional.empty();
        if (!hand.evidence.equals(properties.getOrDefault("used_hand", "none"))) return Optional.empty();
        Integer observedUseTicks = nonNegativeInt(properties.get("client_observed_use_ticks"));
        if (observedUseTicks == null) return Optional.empty();

        TickWindow age = context.timing().observationAgeWindow();
        long serverElapsedMax = saturatingAdd(observedUseTicks.longValue(), age.latest());
        long earliestModeledUseTick = Math.max(BOW_MIN_LEGAL_USE_TICKS, serverElapsedMax);
        long earliestImpactTicks = Math.max(0L, BOW_MIN_LEGAL_USE_TICKS - serverElapsedMax);
        long latestImpactTicks = saturatingAdd(earliestImpactTicks, reactionTicks(context));
        long latestModeledUseTick = Math.max(
            BOW_MIN_LEGAL_USE_TICKS,
            saturatingAdd(serverElapsedMax, latestImpactTicks)
        );
        int boundedUseTicks = latestModeledUseTick >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int)latestModeledUseTick;
        float power = bowPowerForTime(boundedUseTicks);
        double speed = power * 3.0d;
        float rawDamage = bowArrowRawDamage(power, speed);
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("client_observed_use_ticks", Integer.toString(observedUseTicks));
        evidence.put("observation_age_min", Long.toString(age.earliest()));
        evidence.put("observation_age_max", Long.toString(age.latest()));
        evidence.put("server_use_elapsed_max", Long.toString(serverElapsedMax));
        evidence.put("earliest_lethal_use_tick", Long.toString(earliestModeledUseTick));
        evidence.put("latest_modeled_use_tick", Long.toString(latestModeledUseTick));
        evidence.put("bow_power_max", Float.toString(power));
        return Optional.of(new Release(
            "bow_arrow",
            speed,
            rawDamage,
            earliestImpactTicks,
            EnumSet.of(DamageFlag.IS_PROJECTILE),
            "minecraft:arrow",
            true,
            evidence
        ));
    }

    /** Mirrors Minecraft 26.1.2 BowItem.getPowerForTime. */
    private static float bowPowerForTime(int timeHeld) {
        float power = timeHeld / 20.0f;
        power = (power * power + power * 2.0f) / 3.0f;
        return Math.min(power, 1.0f);
    }

    /**
     * Baseline vanilla 26.1.2 arrow damage for the visible draw power. Full draw is critical, so
     * include AbstractArrow's maximum critical random addition. Visible enchantment widening is a
     * separate release-profile task and must not be guessed here.
     */
    private static float bowArrowRawDamage(float power, double speed) {
        long base = (long)Math.ceil(Math.max(0d, speed * ARROW_BASE_DAMAGE));
        long damage = base;
        if (power >= 1.0f) {
            damage += base / 2L + 1L;
        }
        return damage >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)damage;
    }

    private static long reactionTicks(PredictionContext context) {
        long reactionTicks = Math.max(
            0L,
            context.timing().nextPacketProcessingWindow().latest() - context.timing().clientTick()
        );
        return Math.min(reactionTicks, context.limits().maxProjectileHorizonTicks());
    }

    private static AabbSnapshot sweptOneTick(PredictionContext context) {
        AabbSnapshot box = context.player().boundingBox();
        Vec3Snapshot velocity = context.player().velocity();
        return new AabbSnapshot(
            Math.min(box.minX(), box.minX() + velocity.x()),
            Math.min(box.minY(), box.minY() + velocity.y()),
            Math.min(box.minZ(), box.minZ() + velocity.z()),
            Math.max(box.maxX(), box.maxX() + velocity.x()),
            Math.max(box.maxY(), box.maxY() + velocity.y()),
            Math.max(box.maxZ(), box.maxZ() + velocity.z())
        );
    }

    private static boolean withinFirstTickReach(Vec3Snapshot eye, AabbSnapshot target, double speed) {
        if (!Double.isFinite(speed) || speed <= 0d) return false;
        return distanceToAabb(eye, target) <= speed;
    }

    private static double distanceToAabb(Vec3Snapshot point, AabbSnapshot box) {
        double dx = axisDistance(point.x(), box.minX(), box.maxX());
        double dy = axisDistance(point.y(), box.minY(), box.maxY());
        double dz = axisDistance(point.z(), box.minZ(), box.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0d;
    }

    private static Vec3Snapshot eyePosition(WorldSnapshot.EntitySnapshot attacker) {
        Double x = finiteDouble(attacker.properties().get("eye_position_x"));
        Double y = finiteDouble(attacker.properties().get("eye_position_y"));
        Double z = finiteDouble(attacker.properties().get("eye_position_z"));
        return x == null || y == null || z == null ? null : new Vec3Snapshot(x, y, z);
    }

    private static float fireworkRawDamage(int explosions) {
        double value = 5d + 2d * explosions;
        return value >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)value;
    }

    private static long saturatingAdd(long value, long increment) {
        return increment > 0L && value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static Double finiteDouble(String value) {
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Float positiveFloat(String value) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed > 0f ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer nonNegativeInt(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private enum Hand {
        MAIN_HAND("main_hand_item_key", "main_hand_", "main_hand"),
        OFF_HAND("offhand_item_key", "off_hand_", "off_hand");

        private final String itemKeyProperty;
        private final String prefix;
        private final String evidence;

        Hand(String itemKeyProperty, String prefix, String evidence) {
            this.itemKeyProperty = itemKeyProperty;
            this.prefix = prefix;
            this.evidence = evidence;
        }
    }

    private record Release(
        String family,
        double speed,
        float rawDamage,
        long earliestImpactTicks,
        EnumSet<DamageFlag> flags,
        String sourceKey,
        boolean blockable,
        Map<String, String> evidence
    ) {
        private Release {
            if (earliestImpactTicks < 0L) throw new IllegalArgumentException("earliestImpactTicks must be non-negative");
            flags = flags.clone();
            evidence = Map.copyOf(evidence);
        }
    }
}
