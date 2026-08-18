package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
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
import java.util.Optional;

public final class AreaEffectCloudPredictor implements ThreatPredictor {
    private static final String CLOUD_TYPE = "minecraft:area_effect_cloud";
    private static final int DEFAULT_REAPPLICATION_DELAY = 20;

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> events = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!CLOUD_TYPE.equals(entity.typeKey())) continue;
            addCloudThreats(context, entity, events);
        }
        return List.copyOf(events);
    }

    private static void addCloudThreats(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot cloud,
        List<ThreatEvent> output
    ) {
        float rawDamage = finitePositiveFloat(cloud.properties().get("cloud_instant_damage"), 0f);
        if (rawDamage <= 0f) return;

        int remaining = nonNegativeInt(cloud.properties().get("cloud_duration_remaining_ticks"), 0);
        if (remaining <= 0) return;
        if (!intersects(context.player().boundingBox(), cloud.boundingBox())) return;

        int waitRemaining = nonNegativeInt(cloud.properties().get("cloud_wait_remaining_ticks"), 0);
        int reapplicationDelay = Math.max(
            1,
            nonNegativeInt(
                cloud.properties().get("cloud_reapplication_delay_ticks"),
                DEFAULT_REAPPLICATION_DELAY
            )
        );
        int observationAge = nonNegativeInt(cloud.properties().get("observation_age_ticks"), 0);
        long horizon = Math.min((long) context.limits().maxDecisionHistory(), remaining);
        if (horizon < 0) return;

        if (waitRemaining > 0) {
            long earliest = Math.max(0L, (long) waitRemaining - observationAge);
            long latest = Math.min(horizon, waitRemaining);
            if (earliest <= latest) {
                output.add(event(cloud, 0, new TickWindow(earliest, latest), rawDamage));
            }

            long nextEarliest = earliest + reapplicationDelay;
            long nextLatest = latest + reapplicationDelay;
            int index = 1;
            while (nextEarliest <= horizon) {
                output.add(event(
                    cloud,
                    index++,
                    new TickWindow(nextEarliest, Math.min(horizon, nextLatest)),
                    rawDamage
                ));
                nextEarliest += reapplicationDelay;
                nextLatest += reapplicationDelay;
            }
            return;
        }

        // The per-victim reapplication map is authoritative server state and is not synchronized to the client.
        // Never credit an unknown cooldown as safety. Emit one bounded imminent application; the live engine
        // re-captures the cloud every client tick and therefore re-evaluates this bound continuously.
        output.add(event(
            cloud,
            0,
            new TickWindow(0L, Math.min(horizon, reapplicationDelay)),
            rawDamage
        ));
    }

    private static ThreatEvent event(
        WorldSnapshot.EntitySnapshot cloud,
        int index,
        TickWindow impact,
        float rawDamage
    ) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
            false,
            1f,
            false,
            Optional.of(cloud.position()),
            cloud.properties().getOrDefault("cloud_source_key", "minecraft:indirect_magic")
        );
        return new ThreatEvent(
            "env:area_effect_cloud:" + cloud.id() + ":" + index,
            ThreatKind.ENVIRONMENT,
            impact,
            source,
            Confidence.BOUNDED,
            Optional.of(cloud.position()),
            Optional.of(cloud.position()),
            true,
            false,
            true,
            false
        );
    }

    private static boolean intersects(AabbSnapshot first, AabbSnapshot second) {
        return first.maxX() >= second.minX() && first.minX() <= second.maxX()
            && first.maxY() >= second.minY() && first.minY() <= second.maxY()
            && first.maxZ() >= second.minZ() && first.minZ() <= second.maxZ();
    }

    private static int nonNegativeInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float finitePositiveFloat(String value, float fallback) {
        if (value == null) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed > 0f ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
