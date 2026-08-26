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
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.threat.ExplosionSpec;
import dev.pixelied.survival.threat.ExplosionThreatFactory;
import dev.pixelied.survival.threat.SnapshotOcclusionView;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Predicts legal place-then-break End Crystal bursts before the crystal entity exists. */
public final class CrystalOpportunityPredictor implements LethalOpportunityPredictor {
    private static final String END_CRYSTAL_ITEM = "minecraft:end_crystal";
    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final String BEDROCK = "minecraft:bedrock";
    private static final double CRYSTAL_HALF_WIDTH = 1.0d;
    private static final double CRYSTAL_HEIGHT = 2.0d;
    private static final double SERVER_USE_ON_RANGE_BUFFER = 1.0d;
    private static final double SERVER_ATTACK_RANGE_BUFFER = 3.0d;
    private static final float MAX_END_CRYSTAL_RAW_DAMAGE = 85f;

    private final ExplosionThreatFactory explosionFactory = new ExplosionThreatFactory();
    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();

    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<LethalOpportunity> result = new ArrayList<>();
        List<WorldSnapshot.BlockSnapshot> blocks = context.world().blocks();
        Set<BlockCell> observedBlockCells = blockCells(blocks);
        SnapshotOcclusionView world = new SnapshotOcclusionView(
            blocks.stream().filter(WorldSnapshot.BlockSnapshot::collision).toList()
        );
        int narrowPhaseBudget = context.limits().maxOpportunities();
        int narrowPhaseEvaluations = 0;

        for (WorldSnapshot.EntitySnapshot attacker : context.world().entities()) {
            if (!"minecraft:player".equals(attacker.typeKey())) continue;
            boolean visibleCrystal = holdsCrystal(attacker.properties());
            if (!visibleCrystal && context.safetyMode() != SafetyMode.SAFE) continue;

            Vec3Snapshot eye = eyePosition(attacker);
            Double blockRange = positiveDouble(attacker.properties().get("block_interaction_range"));
            Double entityRange = positiveDouble(attacker.properties().get("attack_range"));
            if (eye == null || blockRange == null || entityRange == null) continue;
            AttackProfile attackProfile = postPlacementAttackProfile(attacker.properties(), entityRange);

            for (WorldSnapshot.BlockSnapshot support : blocks) {
                if (!isCrystalSupport(support.blockId())) continue;
                int x = (int)Math.floor(support.position().x());
                int y = (int)Math.floor(support.position().y());
                int z = (int)Math.floor(support.position().z());
                AabbSnapshot supportBox = new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
                if (!withinRange(eye, supportBox, blockRange + SERVER_USE_ON_RANGE_BUFFER)) continue;
                if (hasObservedBlockAt(observedBlockCells, x, y + 1, z)) continue;

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
                if (!withinServerAttackRange(eye, placedCrystal, attackProfile)) continue;

                if (narrowPhaseEvaluations >= narrowPhaseBudget) {
                    if (result.size() >= context.limits().maxOpportunities()) result.removeLast();
                    result.add(overflowOpportunity(context, attacker, visibleCrystal));
                    return List.copyOf(result);
                }
                narrowPhaseEvaluations++;

                long reactionTicks = reactionTicks(context);
                TickWindow impact = new TickWindow(0, reactionTicks);
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
                evidence.put("server_use_on_range_buffer", Double.toString(SERVER_USE_ON_RANGE_BUFFER));
                evidence.put("entity_interaction_range", Double.toString(entityRange));
                evidence.put("server_attack_range_buffer", Double.toString(SERVER_ATTACK_RANGE_BUFFER));
                evidence.put("attack_profile", attackProfile.source());
                evidence.put("attack_min_range", Double.toString(attackProfile.minRange()));
                evidence.put("attack_max_range", Double.toString(attackProfile.maxRange()));
                evidence.put("attack_hitbox_margin", Double.toString(attackProfile.hitboxMargin()));
                result.add(new LethalOpportunity(
                    id,
                    OpportunityFamily.CRYSTAL,
                    projected,
                    Confidence.POTENTIAL,
                    visibleCrystal ? 2 : 3,
                    evidence
                ));
            }
        }
        return List.copyOf(result);
    }

    private static LethalOpportunity overflowOpportunity(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot attacker,
        boolean visibleCrystal
    ) {
        String id = "opportunity:crystal:overflow:" + attacker.id();
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            new DamageRange(0f, MAX_END_CRYSTAL_RAW_DAMAGE),
            Set.of(DamageFlag.IS_EXPLOSION),
            true,
            1f,
            false,
            Optional.empty(),
            "minecraft:explosion"
        );
        ThreatEvent projected = new ThreatEvent(
            id,
            ThreatKind.EXPLOSION,
            new TickWindow(0, reactionTicks(context)),
            damage,
            Confidence.POTENTIAL,
            Optional.empty(),
            Optional.empty(),
            true,
            true,
            true,
            false
        );
        return new LethalOpportunity(
            id,
            OpportunityFamily.CRYSTAL,
            projected,
            Confidence.POTENTIAL,
            visibleCrystal ? 2 : 3,
            Map.of(
                "attacker_id", attacker.id(),
                "visible_crystal", Boolean.toString(visibleCrystal),
                "budget_overflow", "true",
                "narrow_phase_budget", Integer.toString(context.limits().maxOpportunities()),
                "unscanned_candidates", "true"
            )
        );
    }

    private static long reactionTicks(PredictionContext context) {
        long reactionTicks = Math.max(
            0L,
            context.timing().nextPacketProcessingWindow().latest() - context.timing().clientTick()
        );
        return Math.min(reactionTicks, context.limits().maxProjectileHorizonTicks());
    }

    private static AttackProfile postPlacementAttackProfile(Map<String, String> properties, double entityRange) {
        boolean mainCrystal = END_CRYSTAL_ITEM.equals(properties.get("main_hand_item_key"));
        boolean offhandCrystal = END_CRYSTAL_ITEM.equals(properties.get("offhand_item_key"));
        Integer mainCount = positiveInt(properties.get("main_hand_count"));

        // ServerboundAttackPacket always evaluates the player's main-hand stack. An off-hand
        // placement leaves the main hand untouched. A main-hand placement only changes the attack
        // profile when the placed crystal consumes the last item in that stack.
        if (offhandCrystal || mainCrystal && mainCount != null && mainCount > 1) {
            return currentMainHandAttackProfile(properties, entityRange);
        }
        if (mainCrystal) return defaultAttackProfile(entityRange, "post_place_default");

        // SAFE may model a legal hidden-slot change before placement. Without observable stack
        // components, do not invent a custom post-place weapon profile; use the synchronized
        // default entity interaction range after a potentially consumed singleton crystal.
        return defaultAttackProfile(entityRange, "post_place_default");
    }

    private static AttackProfile currentMainHandAttackProfile(Map<String, String> properties, double entityRange) {
        Double min = nonNegativeDouble(properties.get("main_hand_attack_min_range"));
        Double max = nonNegativeDouble(properties.get("main_hand_attack_max_range"));
        Double margin = nonNegativeDouble(properties.get("main_hand_attack_hitbox_margin"));
        if (min == null || max == null || margin == null || min > max) {
            return defaultAttackProfile(entityRange, "current_main_hand");
        }
        return new AttackProfile(min, max, margin, "current_main_hand");
    }

    private static AttackProfile defaultAttackProfile(double entityRange, String source) {
        return new AttackProfile(0d, entityRange, 0d, source);
    }

    private static boolean withinServerAttackRange(
        Vec3Snapshot eye,
        AabbSnapshot target,
        AttackProfile profile
    ) {
        double distance = Math.sqrt(distanceToSqr(eye, target));
        double min = profile.minRange() - profile.hitboxMargin() - SERVER_ATTACK_RANGE_BUFFER;
        double max = profile.maxRange() + profile.hitboxMargin() + SERVER_ATTACK_RANGE_BUFFER;
        return distance >= min && distance <= max;
    }

    private static Set<BlockCell> blockCells(List<WorldSnapshot.BlockSnapshot> blocks) {
        Set<BlockCell> result = new HashSet<>(Math.max(16, blocks.size() * 2));
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            result.add(new BlockCell(
                (int)Math.floor(block.position().x()),
                (int)Math.floor(block.position().y()),
                (int)Math.floor(block.position().z())
            ));
        }
        return result;
    }

    private static boolean hasObservedBlockAt(Set<BlockCell> blocks, int x, int y, int z) {
        return blocks.contains(new BlockCell(x, y, z));
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

    private static Integer positiveInt(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double positiveDouble(String value) {
        Double parsed = finiteDouble(value);
        return parsed != null && parsed > 0d ? parsed : null;
    }

    private static Double nonNegativeDouble(String value) {
        Double parsed = finiteDouble(value);
        return parsed != null && parsed >= 0d ? parsed : null;
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

    private record BlockCell(int x, int y, int z) {
    }

    private record AttackProfile(double minRange, double maxRange, double hitboxMargin, String source) {
    }
}
