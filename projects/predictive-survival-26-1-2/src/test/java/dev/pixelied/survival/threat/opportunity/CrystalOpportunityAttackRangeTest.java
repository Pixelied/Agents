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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalOpportunityAttackRangeTest {
    @Test
    void serverAttackBufferAndHitboxMarginReachInclusiveBoundary() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(8.0, 0.0, 0.5),
            "minecraft:stick",
            "minecraft:end_crystal",
            1,
            4.5,
            1.0,
            0.0,
            1.0,
            0.5
        );

        List<LethalOpportunity> opportunities = predict(attacker);

        assertEquals(1, opportunities.size());
        assertEquals("3.0", opportunities.getFirst().evidence().get("server_attack_range_buffer"));
        assertEquals("current_main_hand", opportunities.getFirst().evidence().get("attack_profile"));
    }

    @Test
    void serverMinimumAttackRangeRejectsPointBlankBreak() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(3.5, 0.0, 0.5),
            "minecraft:stick",
            "minecraft:end_crystal",
            1,
            4.5,
            3.0,
            4.0,
            8.0,
            0.0
        );

        assertTrue(predict(attacker).isEmpty());
    }

    @Test
    void singleMainHandCrystalFallsBackToDefaultRangeAfterPlacementConsumesStack() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(8.0, 0.0, 0.5),
            "minecraft:end_crystal",
            "minecraft:air",
            1,
            4.5,
            3.0,
            10.0,
            10.0,
            0.0
        );

        List<LethalOpportunity> opportunities = predict(attacker);

        assertEquals(1, opportunities.size());
        assertEquals("post_place_default", opportunities.getFirst().evidence().get("attack_profile"));
    }

    @Test
    void remainingMainHandCrystalKeepsItsCurrentAttackRangeProfile() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(3.5, 0.0, 0.5),
            "minecraft:end_crystal",
            "minecraft:air",
            2,
            4.5,
            3.0,
            5.0,
            8.0,
            0.0
        );

        assertTrue(predict(attacker).isEmpty());
    }

    private static List<LethalOpportunity> predict(WorldSnapshot.EntitySnapshot attacker) {
        WorldSnapshot.BlockSnapshot support = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(2.5, 0.5, 0.5),
            "minecraft:obsidian",
            true,
            List.of(new AabbSnapshot(2, 0, 0, 3, 1, 1)),
            Map.of("full_collision_cube", "true")
        );
        return new CrystalOpportunityPredictor().predict(context(List.of(attacker), List.of(support)));
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

    private static WorldSnapshot.EntitySnapshot attacker(
        Vec3Snapshot position,
        String mainHand,
        String offhand,
        int mainHandCount,
        double blockRange,
        double entityRange,
        double mainHandMin,
        double mainHandMax,
        double hitboxMargin
    ) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("block_interaction_range", Double.toString(blockRange));
        properties.put("attack_range", Double.toString(entityRange));
        properties.put("main_hand_item_key", mainHand);
        properties.put("offhand_item_key", offhand);
        properties.put("main_hand_count", Integer.toString(mainHandCount));
        properties.put("main_hand_attack_min_range", Double.toString(mainHandMin));
        properties.put("main_hand_attack_max_range", Double.toString(mainHandMax));
        properties.put("main_hand_attack_hitbox_margin", Double.toString(hitboxMargin));
        properties.put("eye_position_x", Double.toString(position.x()));
        properties.put("eye_position_y", "1.62");
        properties.put("eye_position_z", Double.toString(position.z()));
        return new WorldSnapshot.EntitySnapshot(
            "attacker",
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
}
