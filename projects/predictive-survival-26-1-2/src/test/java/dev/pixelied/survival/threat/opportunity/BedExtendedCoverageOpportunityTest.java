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

class BedExtendedCoverageOpportunityTest {
    @Test
    void explicitlyObservedTargetOutsideLegacyEightBlockCubeStillCreatesLethalBedOpportunity() {
        PlayerSnapshot player = new PlayerSnapshot(
            4f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            Map.of()
        );
        WorldSnapshot.EntitySnapshot attacker = new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            new Vec3Snapshot(9.5, 0.0, 4.5),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(9.2, 0.0, 4.2, 9.8, 1.8, 4.8),
            Map.of(
                "block_interaction_range", "4.5",
                "main_hand_item_key", "minecraft:red_bed",
                "offhand_item_key", "minecraft:air",
                "eye_position_x", "9.5",
                "eye_position_y", "1.62",
                "eye_position_z", "4.5",
                "horizontal_facing", "west",
                "bed_explodes", "true"
            )
        );
        WorldSnapshot.BlockSnapshot target = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(9.5, 0.5, 0.5),
            "minecraft:stone",
            true,
            List.of(new AabbSnapshot(9, 0, 0, 10, 1, 1)),
            Map.of("full_collision_cube", "true", "replaceable", "false")
        );
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), List.of(target)),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            SafetyMode.BALANCED
        );

        LethalOpportunity opportunity = new BedOpportunityPredictor().predict(context).stream()
            .filter(value -> "9,1,0".equals(value.evidence().get("foot")))
            .filter(value -> "8,1,0".equals(value.evidence().get("head")))
            .findFirst()
            .orElseThrow();

        assertEquals(OpportunityFamily.BED, opportunity.family());
    }
}
