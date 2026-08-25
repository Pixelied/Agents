package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.VanillaDamageOracle;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.threat.ExplosionSpec;
import dev.pixelied.survival.threat.ExplosionThreatFactory;
import dev.pixelied.survival.threat.SnapshotOcclusionView;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Predicts legal hostile respawn-anchor use sequences before the bad-respawn explosion is triggered. */
public final class RespawnAnchorOpportunityPredictor implements LethalOpportunityPredictor {
    private static final String RESPAWN_ANCHOR = "minecraft:respawn_anchor";
    private static final String GLOWSTONE = "minecraft:glowstone";

    private final ExplosionThreatFactory explosionFactory = new ExplosionThreatFactory();
    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();

    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<LethalOpportunity> result = new ArrayList<>();

        for (WorldSnapshot.EntitySnapshot attacker : context.world().entities()) {
            if (!"minecraft:player".equals(attacker.typeKey())) continue;
            Vec3Snapshot eye = eyePosition(attacker);
            Double blockRange = positiveDouble(attacker.properties().get("block_interaction_range"));
            if (eye == null || blockRange == null) continue;

            boolean visibleGlowstone = holds(attacker.properties(), GLOWSTONE);
            for (WorldSnapshot.BlockSnapshot anchor : context.world().blocks()) {
                if (!RESPAWN_ANCHOR.equals(anchor.blockId())) continue;
                if (!Boolean.parseBoolean(anchor.properties().getOrDefault("anchor_explodes", "false"))) continue;

                Integer charge = anchorCharge(anchor.properties().get("anchor_charge"));
                if (charge == null) continue;
                if (charge == 0 && !visibleGlowstone && context.safetyMode() != SafetyMode.SAFE) continue;

                int x = (int)Math.floor(anchor.position().x());
                int y = (int)Math.floor(anchor.position().y());
                int z = (int)Math.floor(anchor.position().z());
                AabbSnapshot anchorBox = new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
                if (!withinRange(eye, anchorBox, blockRange)) continue;

                long reactionTicks = Math.max(
                    0L,
                    context.timing().nextPacketProcessingWindow().latest() - context.timing().clientTick()
                );
                long latest = Math.min(reactionTicks, context.limits().maxProjectileHorizonTicks());
                TickWindow impact = new TickWindow(0, latest);
                Vec3Snapshot center = new Vec3Snapshot(x + 0.5d, y + 0.5d, z + 0.5d);
                String id = "opportunity:respawn_anchor:" + attacker.id() + ":" + x + "," + y + "," + z;

                List<WorldSnapshot.BlockSnapshot> postRemovalBlocks = context.world().blocks().stream()
                    .filter(block -> block != anchor)
                    .toList();
                ExplosionSpec spec = new ExplosionSpec(
                    center,
                    5f,
                    5f,
                    "minecraft:bad_respawn_point",
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
                    new SnapshotOcclusionView(postRemovalBlocks)
                ).orElse(null);
                if (projected == null) continue;
                if (!damageOracle.lethalWithoutDeathProtection(
                    context.player(), new ThreatTimeline(List.of(projected)))) continue;

                int actionDepth = charge > 0 ? 1 : visibleGlowstone ? 2 : 3;
                Map<String, String> evidence = new LinkedHashMap<>();
                evidence.put("attacker_id", attacker.id());
                evidence.put("anchor", x + "," + y + "," + z);
                evidence.put("anchor_charge", Integer.toString(charge));
                evidence.put("visible_glowstone", Boolean.toString(visibleGlowstone));
                evidence.put("block_interaction_range", Double.toString(blockRange));
                result.add(new LethalOpportunity(
                    id,
                    OpportunityFamily.RESPAWN_ANCHOR,
                    projected,
                    Confidence.POTENTIAL,
                    actionDepth,
                    evidence
                ));
            }
        }
        return List.copyOf(result);
    }

    private static boolean holds(Map<String, String> properties, String itemKey) {
        return itemKey.equals(properties.get("main_hand_item_key"))
            || itemKey.equals(properties.get("offhand_item_key"));
    }

    private static Vec3Snapshot eyePosition(WorldSnapshot.EntitySnapshot attacker) {
        Double x = finiteDouble(attacker.properties().get("eye_position_x"));
        Double y = finiteDouble(attacker.properties().get("eye_position_y"));
        Double z = finiteDouble(attacker.properties().get("eye_position_z"));
        return x == null || y == null || z == null ? null : new Vec3Snapshot(x, y, z);
    }

    private static Integer anchorCharge(String value) {
        if (value == null) return null;
        try {
            int charge = Integer.parseInt(value);
            return charge >= 0 && charge <= 4 ? charge : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
