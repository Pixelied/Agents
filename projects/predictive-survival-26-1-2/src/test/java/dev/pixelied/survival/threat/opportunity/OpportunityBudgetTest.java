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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpportunityBudgetTest {
    @Test
    void thousandsOfCrystalSupportsAreBoundedAndFailClosedOnOverflow() {
        EngineLimits limits = new EngineLimits(128, 32, 80, 128, 64);
        List<LethalOpportunity> result = new CrystalOpportunityPredictor().predict(context(limits, 4_000));

        assertTrue(result.size() <= limits.maxOpportunities(), "predictor must bound narrow-phase opportunity work");
        assertTrue(
            result.stream().anyMatch(opportunity -> "true".equals(opportunity.evidence().get("budget_overflow"))),
            "overflow must remain dangerous instead of silently dropping unscanned candidates"
        );
    }

    private static PredictionContext context(EngineLimits limits, int supportCount) {
        PlayerSnapshot player = new PlayerSnapshot(
            1f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            Map.of()
        );

        WorldSnapshot.EntitySnapshot attacker = new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            new Vec3Snapshot(0.5, 0.0, 0.5),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(0.2, 0.0, 0.2, 0.8, 1.8, 0.8),
            Map.ofEntries(
                Map.entry("main_hand_item_key", "minecraft:stick"),
                Map.entry("offhand_item_key", "minecraft:end_crystal"),
                Map.entry("main_hand_count", "1"),
                Map.entry("block_interaction_range", "16.0"),
                Map.entry("attack_range", "16.0"),
                Map.entry("main_hand_attack_min_range", "0.0"),
                Map.entry("main_hand_attack_max_range", "16.0"),
                Map.entry("main_hand_attack_hitbox_margin", "0.0"),
                Map.entry("eye_position_x", "0.5"),
                Map.entry("eye_position_y", "1.62"),
                Map.entry("eye_position_z", "0.5")
            )
        );

        List<WorldSnapshot.BlockSnapshot> supports = new ArrayList<>(supportCount);
        for (int i = 0; i < supportCount; i++) {
            supports.add(new WorldSnapshot.BlockSnapshot(
                new Vec3Snapshot(1.5, 0.5, 0.5),
                "minecraft:obsidian",
                false,
                List.of(),
                Map.of()
            ));
        }

        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), supports),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            limits,
            SafetyMode.BALANCED
        );
    }
}
