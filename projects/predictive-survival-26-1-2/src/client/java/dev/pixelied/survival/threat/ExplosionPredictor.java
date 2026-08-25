package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExplosionPredictor implements ThreatPredictor {
    private final ExplosionThreatFactory threatFactory = new ExplosionThreatFactory();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        OcclusionView world = new SnapshotOcclusionView(context.world().blocks(), List.of());
        List<ThreatEvent> events = new ArrayList<>();

        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            buildEvent(
                "explosion:" + entity.id(), entity.position(), entity.velocity(), entity.properties(), context, world
            ).ifPresent(events::add);
        }
        for (WorldSnapshot.BlockSnapshot block : context.world().blocks()) {
            OcclusionView eventWorld = withoutPreExplosionRemovedBlocks(context.world().blocks(), block);
            buildEvent(
                "explosion:block:" + block.blockId() + ":" + block.position(), block.position(),
                new Vec3Snapshot(0, 0, 0), block.properties(), context, eventWorld
            ).ifPresent(events::add);
        }
        return List.copyOf(events);
    }

    private Optional<ThreatEvent> buildEvent(
        String id,
        Vec3Snapshot center,
        Vec3Snapshot sourceVelocity,
        Map<String, String> properties,
        PredictionContext context,
        OcclusionView world
    ) {
        RadiusResolution radiusResolution = resolveRadius(properties, context.safetyMode());
        if (radiusResolution == null) return Optional.empty();
        RadiusRange radius = radiusResolution.radius();

        TickWindow impact;
        Confidence confidence;
        boolean triggerable = Boolean.parseBoolean(properties.getOrDefault("triggerable", "false"));
        Integer fuse = parseNonNegativeInt(properties.get("fuse_ticks"));
        if (fuse != null) {
            if (Boolean.parseBoolean(properties.getOrDefault("countdown_server_synchronized", "false"))) {
                impact = ExplosionTiming.ageCountdown(fuse, context.timing().observationAgeWindow());
                if (impact.earliest() > context.limits().maxProjectileHorizonTicks()) return Optional.empty();
                impact = new TickWindow(
                    impact.earliest(),
                    Math.min(impact.latest(), context.limits().maxProjectileHorizonTicks())
                );
                confidence = impact.earliest() == impact.latest() ? Confidence.EXACT : Confidence.BOUNDED;
            } else {
                if (fuse > context.limits().maxProjectileHorizonTicks()) return Optional.empty();
                impact = new TickWindow(fuse, fuse);
                confidence = Confidence.EXACT;
            }
        } else {
            Integer fuseMin = parseNonNegativeInt(properties.get("fuse_ticks_min"));
            Integer fuseMax = parseNonNegativeInt(properties.get("fuse_ticks_max"));
            if (fuseMin != null && fuseMax != null) {
                if (fuseMin > fuseMax || fuseMin > context.limits().maxProjectileHorizonTicks()) return Optional.empty();
                long latest = Math.min(fuseMax, context.limits().maxProjectileHorizonTicks());
                impact = new TickWindow(fuseMin, latest);
                confidence = Confidence.BOUNDED;
            } else if (triggerable) {
                long reactionTicks = Math.max(
                    0L,
                    context.timing().nextPacketProcessingWindow().latest() - context.timing().clientTick()
                );
                long latest = Math.min(reactionTicks, context.limits().maxProjectileHorizonTicks());
                impact = new TickWindow(0, latest);
                confidence = Confidence.POTENTIAL;
            } else {
                return Optional.empty();
            }
        }
        confidence = lessCertain(confidence, radiusResolution.confidenceFloor());

        ExplosionSpec spec = new ExplosionSpec(
            center,
            radius.min(),
            radius.max(),
            properties.getOrDefault("source_key", "minecraft:explosion"),
            Boolean.parseBoolean(properties.getOrDefault("scales_with_difficulty", "false")),
            !Boolean.parseBoolean(properties.getOrDefault("bypasses_shield", "false"))
        );
        return triggerable
            ? threatFactory.createProjected(id, impact, confidence, spec, sourceVelocity, context, world)
            : threatFactory.create(id, impact, confidence, spec, context, world);
    }

    private static OcclusionView withoutPreExplosionRemovedBlocks(
        List<WorldSnapshot.BlockSnapshot> blocks,
        WorldSnapshot.BlockSnapshot source
    ) {
        String group = source.properties().get("pre_explosion_remove_group");
        if (group == null || group.isBlank()) return new SnapshotOcclusionView(blocks, List.of());
        List<WorldSnapshot.BlockSnapshot> filtered = blocks.stream()
            .filter(block -> !group.equals(block.properties().get("pre_explosion_remove_group")))
            .toList();
        return new SnapshotOcclusionView(filtered, List.of());
    }

    static boolean canUseUnitCubeOcclusion(WorldSnapshot.BlockSnapshot block) {
        return block.collision()
            && Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"));
    }

    private static RadiusResolution resolveRadius(Map<String, String> properties, SafetyMode safetyMode) {
        Float exact = parsePositiveFloat(properties.get("explosion_radius"));
        if (exact != null) return new RadiusResolution(new RadiusRange(exact, exact), Confidence.EXACT);

        Float min = parseNonNegativeFloat(properties.get("explosion_radius_min"));
        Float max = parsePositiveFloat(properties.get("explosion_radius_max"));
        if (min != null && max != null && min <= max) {
            return new RadiusResolution(new RadiusRange(min, max), Confidence.BOUNDED);
        }

        if (!Boolean.parseBoolean(properties.getOrDefault("server_hidden_explosion_power", "false"))) {
            return null;
        }

        Float defaultRadius = parsePositiveFloat(properties.get("explosion_radius_default"));
        Float hiddenMin = parseNonNegativeFloat(properties.get("explosion_radius_hidden_min"));
        Float hiddenMax = parsePositiveFloat(properties.get("explosion_radius_hidden_max"));
        if (safetyMode == SafetyMode.SAFE) {
            if (hiddenMin == null || hiddenMax == null || hiddenMin > hiddenMax) return null;
            return new RadiusResolution(new RadiusRange(hiddenMin, hiddenMax), Confidence.BOUNDED);
        }
        if (defaultRadius == null) return null;
        return new RadiusResolution(new RadiusRange(defaultRadius, defaultRadius), Confidence.POTENTIAL);
    }

    private static Float parsePositiveFloat(String value) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed > 0f ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Float parseNonNegativeFloat(String value) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed >= 0f ? parsed : null;
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

    private static Confidence lessCertain(Confidence first, Confidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private record RadiusResolution(RadiusRange radius, Confidence confidenceFloor) {
    }

    private record RadiusRange(float min, float max) {
    }
}
