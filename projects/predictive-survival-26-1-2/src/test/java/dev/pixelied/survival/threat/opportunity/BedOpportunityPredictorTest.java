package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedOpportunityPredictorTest {
    @Test
    void visibleBedCanPlaceThenUseInExplodingDimension() {
        List<LethalOpportunity> result = predict(
            attacker("minecraft:red_bed", true),
            List.of(fullBlock(2, 0, 0, "minecraft:stone")),
            SafetyMode.BALANCED
        );

        LethalOpportunity opportunity = placement(result, "2,1,0", "1,1,0");
        assertEquals(OpportunityFamily.BED, opportunity.family());
        assertEquals(2, opportunity.actionDepth());
        assertEquals("true", opportunity.evidence().get("visible_bed"));
        assertEquals("west", opportunity.evidence().get("facing"));
        assertTrue(opportunity.projectedThreat().damage().rawDamage().max() > 0f);
    }

    @Test
    void balancedModeRequiresVisibleBedEvidence() {
        List<LethalOpportunity> result = predict(
            attacker("minecraft:air", true),
            List.of(fullBlock(2, 0, 0, "minecraft:stone")),
            SafetyMode.BALANCED
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void safeModeBudgetsSlotChangePlaceAndUseWithoutVisibleBed() {
        List<LethalOpportunity> result = predict(
            attacker("minecraft:air", true),
            List.of(fullBlock(2, 0, 0, "minecraft:stone")),
            SafetyMode.SAFE
        );

        LethalOpportunity opportunity = placement(result, "2,1,0", "1,1,0");
        assertEquals(3, opportunity.actionDepth());
        assertEquals("false", opportunity.evidence().get("visible_bed"));
    }

    @Test
    void normalBedDimensionDoesNotCreateExplosionOpportunity() {
        List<LethalOpportunity> result = predict(
            attacker("minecraft:red_bed", false),
            List.of(fullBlock(2, 0, 0, "minecraft:stone")),
            SafetyMode.BALANCED
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void occupiedHeadCellPreventsThatPlacement() {
        List<LethalOpportunity> result = predict(
            attacker("minecraft:red_bed", true),
            List.of(
                fullBlock(2, 0, 0, "minecraft:stone"),
                fullBlock(1, 1, 0, "minecraft:stone")
            ),
            SafetyMode.BALANCED
        );

        assertTrue(result.stream().noneMatch(opportunity ->
            "2,1,0".equals(opportunity.evidence().get("foot"))
                && "1,1,0".equals(opportunity.evidence().get("head"))
        ));
    }

    @Test
    void placementTargetOutsideBlockInteractionReachDoesNotCreateOpportunity() {
        List<LethalOpportunity> result = predict(
            attackerAt(new Vec3Snapshot(9.0, 0.0, 0.5), "minecraft:red_bed", true),
            List.of(fullBlock(2, 0, 0, "minecraft:stone")),
            SafetyMode.BALANCED
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void projectedExplosionUsesHeadCenterWithoutBedSelfOcclusion() {
        List<LethalOpportunity> result = predict(
            attacker("minecraft:red_bed", true),
            List.of(fullBlock(2, 0, 0, "minecraft:stone")),
            SafetyMode.BALANCED
        );

        LethalOpportunity opportunity = placement(result, "2,1,0", "1,1,0");
        assertEquals(new Vec3Snapshot(1.5, 1.5, 0.5), opportunity.projectedThreat().sourcePosition().orElseThrow());
        assertTrue(opportunity.projectedThreat().damage().rawDamage().max() > 4f,
            "the placed bed halves are removed before the vanilla bad-respawn explosion");
    }

    private static LethalOpportunity placement(List<LethalOpportunity> result, String foot, String head) {
        return result.stream()
            .filter(opportunity -> foot.equals(opportunity.evidence().get("foot")))
            .filter(opportunity -> head.equals(opportunity.evidence().get("head")))
            .findFirst()
            .orElseThrow();
    }

    private static List<LethalOpportunity> predict(
        WorldSnapshot.EntitySnapshot attacker,
        List<WorldSnapshot.BlockSnapshot> blocks,
        SafetyMode mode
    ) {
        return new BedOpportunityPredictor().predict(context(List.of(attacker), blocks, mode));
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks,
        SafetyMode mode
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            4f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, blocks),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            mode
        );
    }

    private static WorldSnapshot.EntitySnapshot attacker(String heldItem, boolean bedExplodes) {
        return attackerAt(new Vec3Snapshot(4.5, 0.0, 0.5), heldItem, bedExplodes);
    }

    private static WorldSnapshot.EntitySnapshot attackerAt(
        Vec3Snapshot position,
        String heldItem,
        boolean bedExplodes
    ) {
        return new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            position,
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(
                position.x() - 0.3, position.y(), position.z() - 0.3,
                position.x() + 0.3, position.y() + 1.8, position.z() + 0.3
            ),
            Map.of(
                "block_interaction_range", "4.5",
                "main_hand_item_key", heldItem,
                "offhand_item_key", "minecraft:air",
                "eye_position_x", Double.toString(position.x()),
                "eye_position_y", "1.62",
                "eye_position_z", Double.toString(position.z()),
                "horizontal_facing", "west",
                "bed_explodes", Boolean.toString(bedExplodes)
            )
        );
    }

    private static WorldSnapshot.BlockSnapshot fullBlock(int x, int y, int z, String blockId) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(x + 0.5, y + 0.5, z + 0.5),
            blockId,
            true,
            List.of(new AabbSnapshot(x, y, z, x + 1.0, y + 1.0, z + 1.0)),
            Map.of("full_collision_cube", "true")
        );
    }
}
