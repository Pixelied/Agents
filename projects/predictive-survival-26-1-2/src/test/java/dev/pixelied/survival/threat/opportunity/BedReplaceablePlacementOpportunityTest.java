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

class BedReplaceablePlacementOpportunityTest {
    @Test
    void replaceableClickedCellCanBecomeBedFoot() {
        WorldSnapshot.BlockSnapshot replaceable = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(2.5, 1.5, 0.5),
            "minecraft:short_grass",
            false,
            List.of(),
            Map.of("full_collision_cube", "false", "replaceable", "true")
        );

        List<LethalOpportunity> opportunities = new BedOpportunityPredictor().predict(
            context(List.of(attacker()), List.of(replaceable))
        );

        LethalOpportunity opportunity = opportunities.stream()
            .filter(value -> "2,1,0".equals(value.evidence().get("foot")))
            .filter(value -> "1,1,0".equals(value.evidence().get("head")))
            .findFirst()
            .orElseThrow();
        assertEquals(OpportunityFamily.BED, opportunity.family());
        assertEquals("2,1,0", opportunity.evidence().get("target"));
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
            EngineLimits.defaults(),
            SafetyMode.BALANCED
        );
    }

    private static WorldSnapshot.EntitySnapshot attacker() {
        Vec3Snapshot position = new Vec3Snapshot(4.5, 0.0, 0.5);
        return new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            position,
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(4.2, 0.0, 0.2, 4.8, 1.8, 0.8),
            Map.of(
                "block_interaction_range", "4.5",
                "main_hand_item_key", "minecraft:red_bed",
                "offhand_item_key", "minecraft:air",
                "eye_position_x", "4.5",
                "eye_position_y", "1.62",
                "eye_position_z", "0.5",
                "horizontal_facing", "west",
                "bed_explodes", "true"
            )
        );
    }
}
