package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.timeline.CausalThreatTimeline;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTransition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ExplosionPredictor implements ThreatPredictor {
    private static final double CAUSAL_DISTANCE_EPSILON = 1.0E-9d;
    private static final double MAX_RAY_POWER_FACTOR = 1.3d;
    private static final double RAY_STEP_DISTANCE = 0.3d;
    private static final double RAY_STEP_POWER_COST = 0.22500001d;
    private static final double BLOCK_CENTER_RADIUS = Math.sqrt(3d) / 2d;
    private static final TickWindow EXPLOSION_PRIMED_TNT_FUSE = new TickWindow(10, 29);
    private static final float DEFAULT_TNT_MAX_RAW_DAMAGE = 57f;

    private final ExplosionThreatFactory threatFactory = new ExplosionThreatFactory();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        OcclusionView world = new SnapshotOcclusionView(context.world().blocks(), List.of());
        List<ThreatEvent> events = new ArrayList<>();

        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            buildEvent(
                entityEventId(entity), resolveCenter(entity.position(), entity.properties()),
                entity.velocity(), entity.properties(), context, world
            ).ifPresent(events::add);
        }
        for (WorldSnapshot.BlockSnapshot block : context.world().blocks()) {
            // Nearby block capture intentionally contains every non-air block needed by collision,
            // fall, and explosion models. Reject blocks with no explosion radius metadata before
            // doing any event-specific occlusion work; rebuilding a collision view for ordinary
            // terrain turns the fixed nearby cube into quadratic per-frame work.
            if (resolveRadius(block.properties(), context.safetyMode()) == null) continue;
            OcclusionView eventWorld = withoutPreExplosionRemovedBlocks(
                context.world().blocks(),
                block,
                world
            );
            buildEvent(
                blockEventId(block), block.position(),
                new Vec3Snapshot(0, 0, 0), block.properties(), context, eventWorld
            ).ifPresent(events::add);
        }
        return List.copyOf(events);
    }

    /**
     * Adds world-source identity and deterministic explosion consequences to an already assembled
     * planning timeline. Events that cannot be tied to an observed world source keep an isolated
     * event-local source id, so causal pruning never aliases unrelated threats.
     */
    public CausalThreatTimeline causalize(PredictionContext context, ThreatTimeline timeline) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");

        Map<String, WorldSnapshot.EntitySnapshot> observedExplosionEntities = new LinkedHashMap<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            observedExplosionEntities.put(entityEventId(entity), entity);
        }
        Map<String, WorldSnapshot.BlockSnapshot> observedExplosionBlocks = new LinkedHashMap<>();
        for (WorldSnapshot.BlockSnapshot block : context.world().blocks()) {
            if (resolveRadius(block.properties(), context.safetyMode()) == null) continue;
            observedExplosionBlocks.put(blockEventId(block), block);
        }

        Map<String, String> sourceIds = new LinkedHashMap<>();
        for (ThreatEvent event : timeline.events()) {
            WorldSnapshot.EntitySnapshot observedEntity = observedExplosionEntities.get(event.id());
            WorldSnapshot.BlockSnapshot observedBlock = observedExplosionBlocks.get(event.id());
            String sourceId = observedEntity != null
                ? entitySourceId(observedEntity)
                : observedBlock != null
                    ? blockSourceId(observedBlock)
                    : "event:" + event.id();
            sourceIds.put(event.id(), sourceId);
        }

        Map<String, List<ThreatTransition>> transitions = new LinkedHashMap<>();
        for (ThreatEvent event : timeline.events()) {
            WorldSnapshot.EntitySnapshot sourceEntity = observedExplosionEntities.get(event.id());
            WorldSnapshot.BlockSnapshot sourceBlock = observedExplosionBlocks.get(event.id());
            if (sourceEntity == null && sourceBlock == null) continue;

            Map<String, String> properties = sourceEntity != null
                ? sourceEntity.properties()
                : sourceBlock.properties();
            RadiusResolution radius = resolveRadius(properties, context.safetyMode());
            if (radius == null || radius.radius().min() <= 0f) continue;
            double guaranteedEntityReach = 2d * radius.radius().min();
            double reachSquared = guaranteedEntityReach * guaranteedEntityReach;
            Vec3Snapshot center = sourceEntity != null
                ? resolveCenter(sourceEntity.position(), properties)
                : sourceBlock.position();
            List<ThreatTransition> after = new ArrayList<>();

            for (WorldSnapshot.EntitySnapshot target : context.world().entities()) {
                if (!"minecraft:end_crystal".equals(target.typeKey())) continue;
                if (sourceEntity != null && target.id().equals(sourceEntity.id())) continue;
                if (distanceSquared(center, target.position()) > reachSquared + CAUSAL_DISTANCE_EPSILON) continue;
                after.add(new ThreatTransition.RemoveSource(entitySourceId(target)));
            }

            // 26.1.2 EndCrystal always uses ExplosionInteraction.BLOCK. TntBlock.wasExploded then
            // conditionally creates a fresh PrimedTnt and shortens its default 80-tick fuse to
            // random.nextInt(20) + 10. The client cannot know the server gamerule or random ray,
            // so any reachable observed TNT block is retained as a potential future branch.
            if (sourceEntity != null && "minecraft:end_crystal".equals(sourceEntity.typeKey())) {
                for (WorldSnapshot.BlockSnapshot block : context.world().blocks()) {
                    if (!"minecraft:tnt".equals(block.blockId())) continue;
                    if (!insidePossibleExplosionRayEnvelope(center, radius.radius().max(), block.position())) continue;
                    after.add(explosionPrimedTnt(event, block));
                }
            }

            if (!after.isEmpty()) transitions.put(event.id(), List.copyOf(after));
        }

        return new CausalThreatTimeline(timeline, sourceIds, transitions);
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

    private static ThreatTransition.SpawnThreat explosionPrimedTnt(
        ThreatEvent trigger,
        WorldSnapshot.BlockSnapshot block
    ) {
        String identity = trigger.id() + ":tnt:" + block.position();
        String sourceId = "spawned:" + identity;
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            new DamageRange(0f, DEFAULT_TNT_MAX_RAW_DAMAGE),
            Set.of(DamageFlag.IS_EXPLOSION),
            true,
            1f,
            false,
            Optional.of(block.position()),
            "minecraft:explosion"
        );
        ThreatEvent event = new ThreatEvent(
            "explosion:" + identity,
            ThreatKind.EXPLOSION,
            EXPLOSION_PRIMED_TNT_FUSE,
            damage,
            Confidence.POTENTIAL,
            Optional.of(block.position()),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
        return new ThreatTransition.SpawnThreat(sourceId, event);
    }

    private static boolean insidePossibleExplosionRayEnvelope(
        Vec3Snapshot center,
        float radius,
        Vec3Snapshot blockCenter
    ) {
        // ServerExplosion starts each ray below 1.3 * radius, samples every 0.3 blocks, and
        // subtracts 0.22500001 power after every step. Ignoring resistance before the TNT gives a
        // conservative upper bound. Add half a block diagonal because snapshots store block centers.
        double maxRaySampleDistance = radius * MAX_RAY_POWER_FACTOR / RAY_STEP_POWER_COST * RAY_STEP_DISTANCE;
        double maxBlockCenterDistance = maxRaySampleDistance + BLOCK_CENTER_RADIUS;
        return distanceSquared(center, blockCenter)
            <= maxBlockCenterDistance * maxBlockCenterDistance + CAUSAL_DISTANCE_EPSILON;
    }

    private static OcclusionView withoutPreExplosionRemovedBlocks(
        List<WorldSnapshot.BlockSnapshot> blocks,
        WorldSnapshot.BlockSnapshot source,
        OcclusionView defaultWorld
    ) {
        String group = source.properties().get("pre_explosion_remove_group");
        if (group == null || group.isBlank()) return defaultWorld;
        List<WorldSnapshot.BlockSnapshot> filtered = blocks.stream()
            .filter(block -> !group.equals(block.properties().get("pre_explosion_remove_group")))
            .toList();
        return new SnapshotOcclusionView(filtered, List.of());
    }

    static boolean canUseUnitCubeOcclusion(WorldSnapshot.BlockSnapshot block) {
        return block.collision()
            && Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"));
    }

    private static String entityEventId(WorldSnapshot.EntitySnapshot entity) {
        return "explosion:" + entity.id();
    }

    private static String blockEventId(WorldSnapshot.BlockSnapshot block) {
        return "explosion:block:" + block.blockId() + ":" + block.position();
    }

    private static String entitySourceId(WorldSnapshot.EntitySnapshot entity) {
        return "entity:" + entity.id();
    }

    private static String blockSourceId(WorldSnapshot.BlockSnapshot block) {
        String group = block.properties().get("pre_explosion_remove_group");
        return group == null || group.isBlank()
            ? "block:" + block.blockId() + ":" + block.position()
            : "block:" + group;
    }

    private static double distanceSquared(Vec3Snapshot first, Vec3Snapshot second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Vec3Snapshot resolveCenter(Vec3Snapshot fallback, Map<String, String> properties) {
        Double x = parseFiniteDouble(properties.get("explosion_center_x"));
        Double y = parseFiniteDouble(properties.get("explosion_center_y"));
        Double z = parseFiniteDouble(properties.get("explosion_center_z"));
        return x == null || y == null || z == null ? fallback : new Vec3Snapshot(x, y, z);
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
        Float defaultMin = parseNonNegativeFloat(properties.get("explosion_radius_default_min"));
        Float defaultMax = parsePositiveFloat(properties.get("explosion_radius_default_max"));
        Float hiddenMin = parseNonNegativeFloat(properties.get("explosion_radius_hidden_min"));
        Float hiddenMax = parsePositiveFloat(properties.get("explosion_radius_hidden_max"));
        if (safetyMode == SafetyMode.SAFE) {
            if (hiddenMin == null || hiddenMax == null || hiddenMin > hiddenMax) return null;
            return new RadiusResolution(new RadiusRange(hiddenMin, hiddenMax), Confidence.BOUNDED);
        }
        if (defaultMin != null && defaultMax != null && defaultMin <= defaultMax) {
            return new RadiusResolution(new RadiusRange(defaultMin, defaultMax), Confidence.POTENTIAL);
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

    private static Confidence lessCertain(Confidence first, Confidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private record RadiusResolution(RadiusRange radius, Confidence confidenceFloor) {
    }

    private record RadiusRange(float min, float max) {
    }
}
