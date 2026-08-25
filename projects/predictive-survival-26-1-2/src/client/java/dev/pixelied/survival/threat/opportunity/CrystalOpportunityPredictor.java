package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.VanillaDamageOracle;
import dev.pixelied.survival.threat.ExplosionSpec;
import dev.pixelied.survival.threat.ExplosionThreatFactory;
import dev.pixelied.survival.threat.SnapshotOcclusionView;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Predicts legal place-then-break End Crystal bursts before the crystal entity exists. */
public final class CrystalOpportunityPredictor implements LethalOpportunityPredictor {
    private static final String END_CRYSTAL_ITEM = "minecraft:end_crystal";
    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final String BEDROCK = "minecraft:bedrock";
    private static final double CRYSTAL_HALF_WIDTH = 1.0d;
    private static final double CRYSTAL_HEIGHT = 2.0d;

    private final ExplosionThreatFactory explosionFactory = new ExplosionThreatFactory();
    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();

    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<LethalOpportunity> result = new ArrayList<>();
        SnapshotOcclusionView world = new SnapshotOcclusionView(context.world().blocks());

        for (WorldSnapshot.EntitySnapshot attacker : context.world().entities()) {
            if (!"minecraft:player".equals(attacker.typeKey())) continue;
            boolean visibleCrystal = holdsCrystal(attacker.properties());
            if (!visibleCrystal) continue;

            Vec3Snapshot eye = eyePosition(attacker);
            Double blockRange = positiveDouble(attacker.properties().get("block_interaction_range"));
            Double entityRange = positiveDouble(attacker.properties().get("attack_range"));
            if (eye == null || blockRange == null || entityRange == null) continue;

            for (WorldSnapshot.BlockSnapshot support : context.world().blocks()) {
                if (!isCrystalSupport(support.blockId())) continue;
                int x = (int)Math.floor(support.position().x());
                int y = (int)Math.floor(support.position().y());
                int z = (int)Math.floor(support.position().z());
                AabbSnapshot supportBox = new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
                if (!withinRange(eye, supportBox, blockRange)) continue;
                if (hasObservedBlockAt(context.world().blocks(), x, y + 1, z)) continue;

                AabbSnapshot placementVolume = new AabbSnapshot(
                    x, y + 1d, z,
                    x + 1d, y + 3d, z + 1d
                );
                if (intersects(context.player().boundingBox(), placementVolume)) continue;
                if (hasObservedEntityIntersection(context.world().entities(), placementVolume)) continue;

                Vec3Snapshot center = new Vec3Snapshot(x + 0.5d, y + 1d, z + 0.5d);
                AabbSnapshot placedCrystal = new AabbSnapshot(
                    center.x() - CRYSTAL_HALF_WIDTH,
                    center.y(),
                    center.z() - CRYSTAL_HALF_WIDTH,
                    center.x() + CRYSTAL_HALF_WIDTH,
                    center.y() + CRYSTAL_HEIGHT,
                    center.z() + CRYSTAL_HALF_WIDTH
                );
                if (!withinRange(eye, placedCrystal, entityRange)) continue;

                long reactionTicks = Math.max(
                    0L,
                    context.timing().nextPacketProcessingWindow().latest() - context.timing().clientTick()
                );
                long latest = Math.min(reactionTicks, context.limits().maxProjectileHorizonTicks());
                TickWindow impact = new TickWindow(0, latest);
                String id = "opportunity:crystal:" + attacker.id() + ":" + x + "," + y + "," + z;
                ExplosionSpec spec = new ExplosionSpec(
                    center,
                    6f,
                    6f,
                    "minecraft:explosion",
                    true,
                    true
                );
                ThreatEvent projected = explosionFactory.createProjected(
                    id,
                    impact,
                    Confidence.POTENTIAL,
                    spec,
                    new Vec3Snapshot(0d, 0d, 0d),
                    context,
                    world
                ).orElse(null);
                if (projected == null) continue;
                if (!damageOracle.lethalWithoutDeathProtection(
                    context.player(), new ThreatTimeline(List.of(projected)))) continue;

                Map<String, String> evidence = new LinkedHashMap<>();
                evidence.put("attacker_id", attacker.id());
                evidence.put("support", x + "," + y + "," + z);
                evidence.put("visible_crystal", Boolean.toString(visibleCrystal));
                evidence.put("block_interaction_range", Double.toString(blockRange));
                evidence.put("entity_interaction_range", Double.toString(entityRange));
                result.add(new LethalOpportunity(
                    id,
                    OpportunityFamily.CRYSTAL,
                    projected,
                    Confidence.POTENTIAL,
                    2,
                    evidence
                ));
            }
        }
        return List.copyOf(result);
    }

    private static boolean hasObservedBlockAt(
        List<WorldSnapshot.BlockSnapshot> blocks,
        int x,
        int y,
        int z
    ) {
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if ((int)Math.floor(block.position().x()) == x
                && (int)Math.floor(block.position().y()) == y
                && (int)Math.floor(block.position().z()) == z) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasObservedEntityIntersection(
        List<WorldSnapshot.EntitySnapshot> entities,
        AabbSnapshot volume
    ) {
        for (WorldSnapshot.EntitySnapshot entity : entities) {
            if (intersects(entity.boundingBox(), volume)) return true;
        }
        return false;
    }

    private static boolean intersects(AabbSnapshot first, AabbSnapshot second) {
        return first.minX() < second.maxX() && first.maxX() > second.minX()
            && first.minY() < second.maxY() && first.maxY() > second.minY()
            && first.minZ() < second.maxZ() && first.maxZ() > second.minZ();
    }

    private static boolean holdsCrystal(Map<String, String> properties) {
        return END_CRYSTAL_ITEM.equals(properties.get("main_hand_item_key"))
            || END_CRYSTAL_ITEM.equals(properties.get("offhand_item_key"));
    }

    private static boolean isCrystalSupport(String blockId) {
        return OBSIDIAN.equals(blockId) || BEDROCK.equals(blockId);
    }

    private static Vec3Snapshot eyePosition(WorldSnapshot.EntitySnapshot attacker) {
        Double x = finiteDouble(attacker.properties().get("eye_position_x"));
        Double y = finiteDouble(attacker.properties().get("eye_position_y"));
        Double z = finiteDouble(attacker.properties().get("eye_position_z"));
        return x == null || y == null || z == null ? null : new Vec3Snapshot(x, y, z);
    }

    private static boolean withinRange(Vec3Snapshot point, AabbSnapshot box, double range) {
        return distanceToSqr(point, box) < range * range;
    }

    private static double distanceToSqr(Vec3Snapshot point, AabbSnapshot box) {
        double dx = axisDistance(point.x(), box.minX(), box.maxX());
        double dy = axisDistance(point.y(), box.minY(), box.maxY());
        double dz = axisDistance(point.z(), box.minZ(), box.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0d;
    }

    private static Double positiveDouble(String value) {
        Double parsed = finiteDouble(value);
        return parsed != null && parsed > 0d ? parsed : null;
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
}
