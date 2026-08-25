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
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalOpportunityPredictorTest {
    @Test
    void legalLethalSupportCreatesOpportunityBeforeCrystalEntityExists() {
        WorldSnapshot.EntitySnapshot attacker = crystalAttacker();
        WorldSnapshot.BlockSnapshot support = fullBlock(2, 0, 0, "minecraft:obsidian");
        PredictionContext context = context(List.of(attacker), List.of(support));

        List<LethalOpportunity> opportunities = new CrystalOpportunityPredictor().predict(context);

        assertEquals(1, opportunities.size());
        LethalOpportunity opportunity = opportunities.getFirst();
        assertEquals(OpportunityFamily.CRYSTAL, opportunity.family());
        assertEquals(2, opportunity.actionDepth());
        assertEquals("true", opportunity.evidence().get("visible_crystal"));
        assertTrue(opportunity.projectedThreat().damage().rawDamage().max() > 0f);
    }

    @Test
    void nonAirBlockAboveSupportPreventsCrystalPlacementOpportunity() {
        WorldSnapshot.EntitySnapshot attacker = crystalAttacker();
        WorldSnapshot.BlockSnapshot support = fullBlock(2, 0, 0, "minecraft:obsidian");
        WorldSnapshot.BlockSnapshot water = nonCollidingBlock(2, 1, 0, "minecraft:water");
        PredictionContext context = context(List.of(attacker), List.of(support, water));

        List<LethalOpportunity> opportunities = new CrystalOpportunityPredictor().predict(context);

        assertTrue(opportunities.isEmpty());
    }

    @Test
    void entityInsidePlacementVolumePreventsCrystalOpportunity() {
        WorldSnapshot.EntitySnapshot attacker = crystalAttacker();
        WorldSnapshot.EntitySnapshot occupant = new WorldSnapshot.EntitySnapshot(
            "occupant",
            "minecraft:item",
            new Vec3Snapshot(2.5, 1.25, 0.5),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(2.25, 1.1, 0.25, 2.75, 1.6, 0.75),
            Map.of()
        );
        WorldSnapshot.BlockSnapshot support = fullBlock(2, 0, 0, "minecraft:obsidian");
        PredictionContext context = context(List.of(attacker, occupant), List.of(support));

        List<LethalOpportunity> opportunities = new CrystalOpportunityPredictor().predict(context);

        assertTrue(opportunities.isEmpty());
    }

    @Test
    void localPlayerInsidePlacementVolumePreventsCrystalOpportunity() {
        WorldSnapshot.EntitySnapshot attacker = crystalAttacker();
        WorldSnapshot.BlockSnapshot support = fullBlock(0, 0, 0, "minecraft:obsidian");
        PredictionContext context = context(List.of(attacker), List.of(support));

        List<LethalOpportunity> opportunities = new CrystalOpportunityPredictor().predict(context);

        assertTrue(opportunities.isEmpty());
    }

    private static WorldSnapshot.EntitySnapshot crystalAttacker() {
        return attacker(
            "attacker",
            new Vec3Snapshot(3.5, 0.0, 0.5),
            Map.of(
                "block_interaction_range", "4.5",
                "attack_range", "3.0",
                "main_hand_item_key", "minecraft:end_crystal",
                "offhand_item_key", "minecraft:air",
                "eye_position_x", "3.5",
                "eye_position_y", "1.62",
                "eye_position_z", "0.5"
            )
        );
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
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
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot attacker(
        String id,
        Vec3Snapshot position,
        Map<String, String> properties
    ) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            "minecraft:player",
            position,
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(
                position.x() - 0.3, position.y(), position.z() - 0.3,
                position.x() + 0.3, position.y() + 1.8, position.z() + 0.3
            ),
            properties
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

    private static WorldSnapshot.BlockSnapshot nonCollidingBlock(int x, int y, int z, String blockId) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(x + 0.5, y + 0.5, z + 0.5),
            blockId,
            false,
            List.of(),
            Map.of("full_collision_cube", "false")
        );
    }
}
