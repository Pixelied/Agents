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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Predicts legal place-then-use hostile bed bursts before either bed half exists. */
public final class BedOpportunityPredictor implements LethalOpportunityPredictor {
    private static final double SERVER_USE_ON_RANGE_BUFFER = 1.0d;
    private static final int NEARBY_HORIZONTAL_RANGE = 8;
    private static final int NEARBY_VERTICAL_RANGE = 12;
    private static final double BED_COLLISION_HEIGHT = 9.0d / 16.0d;
    private static final int[][] USE_ON_FACES = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private final ExplosionThreatFactory explosionFactory = new ExplosionThreatFactory();
    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();

    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");

        Set<BlockCell> occupied = occupiedCells(context.world().blocks());
        Map<String, LethalOpportunity> result = new LinkedHashMap<>();
        SnapshotOcclusionView postExplosionWorld = new SnapshotOcclusionView(context.world().blocks());

        for (WorldSnapshot.EntitySnapshot attacker : context.world().entities()) {
            if (!"minecraft:player".equals(attacker.typeKey())) continue;
            if (!Boolean.parseBoolean(attacker.properties().getOrDefault("bed_explodes", "false"))) continue;

            boolean visibleBed = holdsBed(attacker.properties());
            if (!visibleBed && context.safetyMode() != SafetyMode.SAFE) continue;

            Vec3Snapshot eye = eyePosition(attacker);
            Double blockRange = positiveDouble(attacker.properties().get("block_interaction_range"));
            HorizontalFacing facing = HorizontalFacing.parse(attacker.properties().get("horizontal_facing"));
            if (eye == null || blockRange == null || facing == null) continue;

            for (WorldSnapshot.BlockSnapshot target : context.world().blocks()) {
                if (!target.collision()) continue;
                BlockCell targetCell = cell(target.position());
                if (!withinKnownNearbyAirRegion(context, targetCell)) continue;
                if (!withinServerUseOnRange(eye, targetCell.box(), blockRange)) continue;

                for (int[] face : USE_ON_FACES) {
                    BlockCell foot = targetCell.offset(face[0], face[1], face[2]);
                    BlockCell head = foot.offset(facing.dx, 0, facing.dz);
                    if (!withinKnownNearbyAirRegion(context, foot) || !withinKnownNearbyAirRegion(context, head)) continue;
                    if (occupied.contains(foot) || occupied.contains(head)) continue;
                    if (placementObstructed(context, foot)) continue;

                    Vec3Snapshot center = head.center();
                    long reactionTicks = Math.max(
                        0L,
                        context.timing().nextPacketProcessingWindow().latest() - context.timing().clientTick()
                    );
                    long latest = Math.min(reactionTicks, context.limits().maxProjectileHorizonTicks());
                    TickWindow impact = new TickWindow(0, latest);
                    String id = "opportunity:bed:" + attacker.id() + ":"
                        + foot.serialized() + ":" + head.serialized();

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
                        postExplosionWorld
                    ).orElse(null);
                    if (projected == null) continue;
                    if (!damageOracle.lethalWithoutDeathProtection(
                        context.player(), new ThreatTimeline(List.of(projected)))) continue;

                    Map<String, String> evidence = new LinkedHashMap<>();
                    evidence.put("attacker_id", attacker.id());
                    evidence.put("target", targetCell.serialized());
                    evidence.put("foot", foot.serialized());
                    evidence.put("head", head.serialized());
                    evidence.put("facing", facing.serialized);
                    evidence.put("visible_bed", Boolean.toString(visibleBed));
                    evidence.put("block_interaction_range", Double.toString(blockRange));
                    evidence.put("server_use_on_range_buffer", Double.toString(SERVER_USE_ON_RANGE_BUFFER));
                    evidence.put("server_may_interact", "unknown");
                    result.putIfAbsent(id, new LethalOpportunity(
                        id,
                        OpportunityFamily.BED,
                        projected,
                        Confidence.POTENTIAL,
                        visibleBed ? 2 : 3,
                        evidence
                    ));
                }
            }
        }
        return List.copyOf(result.values());
    }

    private static boolean placementObstructed(PredictionContext context, BlockCell foot) {
        AabbSnapshot body = new AabbSnapshot(
            foot.x, foot.y, foot.z,
            foot.x + 1d, foot.y + BED_COLLISION_HEIGHT, foot.z + 1d
        );
        if (intersects(context.player().boundingBox(), body)) return true;
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (intersects(entity.boundingBox(), body)) return true;
        }
        return false;
    }

    private static boolean withinKnownNearbyAirRegion(PredictionContext context, BlockCell cell) {
        int centerX = (int)Math.floor(context.player().position().x());
        int centerY = (int)Math.floor(context.player().position().y());
        int centerZ = (int)Math.floor(context.player().position().z());
        return Math.abs(cell.x - centerX) <= NEARBY_HORIZONTAL_RANGE
            && Math.abs(cell.z - centerZ) <= NEARBY_HORIZONTAL_RANGE
            && Math.abs(cell.y - centerY) <= NEARBY_VERTICAL_RANGE;
    }

    private static Set<BlockCell> occupiedCells(List<WorldSnapshot.BlockSnapshot> blocks) {
        Set<BlockCell> occupied = new HashSet<>(Math.max(16, blocks.size() * 2));
        for (WorldSnapshot.BlockSnapshot block : blocks) occupied.add(cell(block.position()));
        return occupied;
    }

    private static BlockCell cell(Vec3Snapshot position) {
        return new BlockCell(
            (int)Math.floor(position.x()),
            (int)Math.floor(position.y()),
            (int)Math.floor(position.z())
        );
    }

    private static boolean holdsBed(Map<String, String> properties) {
        return isBedItem(properties.get("main_hand_item_key"))
            || isBedItem(properties.get("offhand_item_key"));
    }

    private static boolean isBedItem(String itemKey) {
        return itemKey != null && itemKey.startsWith("minecraft:") && itemKey.endsWith("_bed");
    }

    private static Vec3Snapshot eyePosition(WorldSnapshot.EntitySnapshot attacker) {
        Double x = finiteDouble(attacker.properties().get("eye_position_x"));
        Double y = finiteDouble(attacker.properties().get("eye_position_y"));
        Double z = finiteDouble(attacker.properties().get("eye_position_z"));
        return x == null || y == null || z == null ? null : new Vec3Snapshot(x, y, z);
    }

    private static boolean withinServerUseOnRange(Vec3Snapshot point, AabbSnapshot box, double range) {
        double maxRange = range + SERVER_USE_ON_RANGE_BUFFER;
        return distanceToSqr(point, box) < maxRange * maxRange;
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

    private static boolean intersects(AabbSnapshot first, AabbSnapshot second) {
        return first.minX() < second.maxX() && first.maxX() > second.minX()
            && first.minY() < second.maxY() && first.maxY() > second.minY()
            && first.minZ() < second.maxZ() && first.maxZ() > second.minZ();
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

    private enum HorizontalFacing {
        NORTH("north", 0, -1),
        SOUTH("south", 0, 1),
        WEST("west", -1, 0),
        EAST("east", 1, 0);

        private final String serialized;
        private final int dx;
        private final int dz;

        HorizontalFacing(String serialized, int dx, int dz) {
            this.serialized = serialized;
            this.dx = dx;
            this.dz = dz;
        }

        private static HorizontalFacing parse(String value) {
            for (HorizontalFacing facing : values()) if (facing.serialized.equals(value)) return facing;
            return null;
        }
    }

    private record BlockCell(int x, int y, int z) {
        private BlockCell offset(int dx, int dy, int dz) {
            return new BlockCell(x + dx, y + dy, z + dz);
        }

        private Vec3Snapshot center() {
            return new Vec3Snapshot(x + 0.5d, y + 0.5d, z + 0.5d);
        }

        private AabbSnapshot box() {
            return new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
        }

        private String serialized() {
            return x + "," + y + "," + z;
        }
    }
}
