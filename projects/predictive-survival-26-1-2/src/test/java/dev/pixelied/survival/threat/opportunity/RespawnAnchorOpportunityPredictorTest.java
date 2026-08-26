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

class RespawnAnchorOpportunityPredictorTest {
    @Test
    void chargedExplosiveDimensionAnchorIsOneInteractionOpportunity() {
        List<LethalOpportunity> result = predict(anchor(1, true), attacker("minecraft:air"), SafetyMode.BALANCED);

        assertEquals(1, result.size());
        assertEquals(OpportunityFamily.RESPAWN_ANCHOR, result.getFirst().family());
        assertEquals(1, result.getFirst().actionDepth());
        assertEquals("1", result.getFirst().evidence().get("anchor_charge"));
        assertTrue(result.getFirst().projectedThreat().damage().rawDamage().max() > 0f);
    }

    @Test
    void unchargedAnchorWithVisibleGlowstoneRequiresChargeThenUse() {
        List<LethalOpportunity> result = predict(anchor(0, true), attacker("minecraft:glowstone"), SafetyMode.BALANCED);

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().actionDepth());
        assertEquals("true", result.getFirst().evidence().get("visible_glowstone"));
    }

    @Test
    void balancedModeRejectsUnchargedAnchorWithoutVisibleGlowstone() {
        List<LethalOpportunity> result = predict(anchor(0, true), attacker("minecraft:air"), SafetyMode.BALANCED);

        assertTrue(result.isEmpty());
    }

    @Test
    void safeModeBudgetsHiddenSlotChargeThenUse() {
        List<LethalOpportunity> result = predict(anchor(0, true), attacker("minecraft:air"), SafetyMode.SAFE);

        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().actionDepth());
        assertEquals("false", result.getFirst().evidence().get("visible_glowstone"));
    }

    @Test
    void anchorThatWorksNormallyDoesNotCreateExplosionOpportunity() {
        List<LethalOpportunity> result = predict(anchor(4, false), attacker("minecraft:air"), SafetyMode.BALANCED);

        assertTrue(result.isEmpty());
    }

    @Test
    void serverUseOnBufferExtendsAnchorInteractionRangeByOneBlock() {
        WorldSnapshot.EntitySnapshot bufferedAttacker = attackerAt(new Vec3Snapshot(7.0, 0.0, 0.5), "minecraft:air");
        List<LethalOpportunity> result = predict(anchor(4, true), bufferedAttacker, SafetyMode.BALANCED);

        assertEquals(1, result.size());
        assertEquals("1.0", result.getFirst().evidence().get("server_use_on_range_buffer"));
    }

    @Test
    void anchorOutsideServerBufferedBlockInteractionRangeDoesNotCreateOpportunity() {
        WorldSnapshot.EntitySnapshot farAttacker = attackerAt(new Vec3Snapshot(8.0, 0.0, 0.5), "minecraft:air");
        List<LethalOpportunity> result = predict(anchor(4, true), farAttacker, SafetyMode.BALANCED);

        assertTrue(result.isEmpty());
    }

    @Test
    void projectedExplosionRemovesAnchorBeforeExposure() {
        List<LethalOpportunity> result = predict(anchor(4, true), attacker("minecraft:air"), SafetyMode.BALANCED);

        assertEquals(1, result.size());
        assertTrue(result.getFirst().projectedThreat().damage().rawDamage().max() > 4f,
            "the source anchor must not shield the player from its own vanilla bad-respawn explosion");
    }

    private static List<LethalOpportunity> predict(
        WorldSnapshot.BlockSnapshot anchor,
        WorldSnapshot.EntitySnapshot attacker,
        SafetyMode mode
    ) {
        return new RespawnAnchorOpportunityPredictor().predict(context(List.of(attacker), List.of(anchor), mode));
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

    private static WorldSnapshot.EntitySnapshot attacker(String heldItem) {
        return attackerAt(new Vec3Snapshot(3.5, 0.0, 0.5), heldItem);
    }

    private static WorldSnapshot.EntitySnapshot attackerAt(Vec3Snapshot position, String heldItem) {
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
                "eye_position_z", Double.toString(position.z())
            )
        );
    }

    private static WorldSnapshot.BlockSnapshot anchor(int charge, boolean explodes) {
        int x = 1;
        int y = 0;
        int z = 0;
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5),
            "minecraft:respawn_anchor",
            true,
            List.of(new AabbSnapshot(x, y, z, x + 1.0, y + 1.0, z + 1.0)),
            Map.of(
                "full_collision_cube", "true",
                "anchor_explodes", Boolean.toString(explodes),
                "anchor_charge", Integer.toString(charge)
            )
        );
    }
}
