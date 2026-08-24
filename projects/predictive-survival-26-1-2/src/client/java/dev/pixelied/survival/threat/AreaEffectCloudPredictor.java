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
        if (!intersects(context.player().boundingBox(), cloud.boundingBox())) return;

        int hazardCount = nonNegativeInt(cloud.properties().get("cloud_hazard_count"), 0);
        if (hazardCount > 0) {
            for (int i = 0; i < hazardCount; i++) {
                addHazard(context, cloud, "cloud_hazard_" + i + "_", i, output);
            }
            return;
        }

        addHazard(context, cloud, "cloud_", 0, output);
    }

    private static void addHazard(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot cloud,
        String prefix,
        int eventIndex,
        List<ThreatEvent> output
    ) {
        float rawDamage = finitePositiveFloat(cloud.properties().get(prefix + "instant_damage"), 0f);
        if (rawDamage <= 0f) return;

        Optional<TickWindow> impact = impactWindow(context, cloud, prefix);
        if (impact.isEmpty()) return;
        output.add(event(cloud, prefix, eventIndex, impact.get(), rawDamage));
    }

    private static Optional<TickWindow> impactWindow(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot cloud,
        String prefix
    ) {
        long horizon = context.limits().maxDecisionHistory();
        Long earliest = nonNegativeLong(cloud.properties().get(prefix + "first_damage_earliest_ticks"));
        Long latest = nonNegativeLong(cloud.properties().get(prefix + "first_damage_latest_ticks"));
        if (earliest != null && latest != null) {
            if (earliest > horizon) return Optional.empty();
            long orderedLatest = Math.max(earliest, latest);
            return Optional.of(new TickWindow(earliest, Math.min(horizon, orderedLatest)));
        }

        int reapplicationDelay = Math.max(
            1,
            nonNegativeInt(
                cloud.properties().get(prefix + "reapplication_delay_ticks"),
                DEFAULT_REAPPLICATION_DELAY
            )
        );
        return Optional.of(new TickWindow(0L, Math.min(horizon, reapplicationDelay)));
    }

    private static ThreatEvent event(
        WorldSnapshot.EntitySnapshot cloud,
        String prefix,
        int eventIndex,
        TickWindow impact,
        float rawDamage
    ) {
        float healthThreshold = finiteNonNegativeFloat(
            cloud.properties().get(prefix + "application_health_threshold_exclusive"),
            0f
        );
        boolean scalesWithDifficulty = Boolean.parseBoolean(
            cloud.properties().getOrDefault(prefix + "scales_with_difficulty", "false")
        );
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
            scalesWithDifficulty,
            1f,
            false,
            Optional.of(cloud.position()),
            cloud.properties().getOrDefault(prefix + "source_key", "minecraft:indirect_magic"),
            healthThreshold
        );
        return new ThreatEvent(
            "env:area_effect_cloud:" + cloud.id() + ":" + eventIndex,
            ThreatKind.ENVIRONMENT,
            impact,
            source,
            impact.earliest() == impact.latest() ? Confidence.EXACT : Confidence.BOUNDED,
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

    private static Long nonNegativeLong(String value) {
        if (value == null) return null;
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0L ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
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

    private static float finiteNonNegativeFloat(String value, float fallback) {
        if (value == null) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed >= 0f ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
