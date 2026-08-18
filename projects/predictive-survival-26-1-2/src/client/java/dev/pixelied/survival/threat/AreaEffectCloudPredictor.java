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
            addCloudThreat(context, entity, events);
        }
        return List.copyOf(events);
    }

    private static void addCloudThreat(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot cloud,
        List<ThreatEvent> output
    ) {
        float rawDamage = finitePositiveFloat(cloud.properties().get("cloud_instant_damage"), 0f);
        if (rawDamage <= 0f) return;
        if (!intersects(context.player().boundingBox(), cloud.boundingBox())) return;

        int reapplicationDelay = Math.max(
            1,
            nonNegativeInt(
                cloud.properties().get("cloud_reapplication_delay_ticks"),
                DEFAULT_REAPPLICATION_DELAY
            )
        );
        long latest = Math.min((long) context.limits().maxDecisionHistory(), reapplicationDelay);

        // The live cloud entity and its causal attribution are observable, but the server's exact wait/victim
        // reapplication phase is not synchronized. Never credit that unknown phase as safety. Emit one bounded
        // imminent application and let the runtime re-capture/re-evaluate the cloud on the next client frame.
        output.add(event(cloud, new TickWindow(0L, latest), rawDamage));
    }

    private static ThreatEvent event(
        WorldSnapshot.EntitySnapshot cloud,
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
            "env:area_effect_cloud:" + cloud.id() + ":0",
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
