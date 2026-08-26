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

class RespawnAnchorWaterExposureTest {
    @Test
    void anchorWaterResistanceDoesNotInventEntityDamageShielding() {
        float dry = projectedDamage(false);
        float sourceWater = projectedDamage(true);

        assertEquals(dry, sourceWater, 1.0E-6f);
    }

    private static float projectedDamage(boolean includeWater) {
        WorldSnapshot.BlockSnapshot anchor = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5),
            "minecraft:respawn_anchor",
            true,
            List.of(new AabbSnapshot(1, 0, 0, 2, 1, 1)),
            Map.of(
                "full_collision_cube", "true",
                "anchor_explodes", "true",
                "anchor_charge", "4"
            )
        );
        WorldSnapshot.BlockSnapshot water = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 1.5, 0.5),
            "minecraft:water",
            false,
            List.of(),
            Map.of("full_collision_cube", "false")
        );
        List<WorldSnapshot.BlockSnapshot> blocks = includeWater ? List.of(anchor, water) : List.of(anchor);

        WorldSnapshot.EntitySnapshot attacker = new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            new Vec3Snapshot(3.5, 0.0, 0.5),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(3.2, 0.0, 0.2, 3.8, 1.8, 0.8),
            Map.of(
                "block_interaction_range", "4.5",
                "main_hand_item_key", "minecraft:air",
                "offhand_item_key", "minecraft:air",
                "eye_position_x", "3.5",
                "eye_position_y", "1.62",
                "eye_position_z", "0.5"
            )
        );

        PlayerSnapshot player = new PlayerSnapshot(
            4f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            Map.of()
        );
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), blocks),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            SafetyMode.BALANCED
        );

        return new RespawnAnchorOpportunityPredictor().predict(context).getFirst()
            .projectedThreat().damage().rawDamage().max();
    }
}
