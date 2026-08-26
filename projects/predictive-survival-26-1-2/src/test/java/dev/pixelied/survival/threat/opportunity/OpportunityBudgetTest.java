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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpportunityBudgetTest {
    @Test
    void thousandsOfCrystalSupportsAreBoundedAndFailClosedOnOverflow() {
        EngineLimits limits = new EngineLimits(128, 32, 80, 128, 64);
        List<LethalOpportunity> result = new CrystalOpportunityPredictor().predict(context(limits, 4_000, 1));

        assertTrue(result.size() <= limits.maxOpportunities(), "predictor must bound narrow-phase opportunity work");
        assertTrue(
            result.stream().anyMatch(opportunity -> "true".equals(opportunity.evidence().get("budget_overflow"))),
            "overflow must remain dangerous instead of silently dropping unscanned candidates"
        );
    }

    @Test
    void opportunityLayerStaysWithinCiTimingBudget() {
        EngineLimits limits = new EngineLimits(128, 32, 80, 128, 64);
        PredictionContext context = context(limits, 4_000, 16);
        CrystalOpportunityPredictor predictor = new CrystalOpportunityPredictor();

        for (int i = 0; i < 25; i++) predictor.predict(context);

        long[] elapsedNanos = new long[200];
        for (int i = 0; i < elapsedNanos.length; i++) {
            long start = System.nanoTime();
            predictor.predict(context);
            elapsedNanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(elapsedNanos);

        double medianMillis = elapsedNanos[elapsedNanos.length / 2] / 1_000_000.0;
        int p95Index = (int)Math.ceil(elapsedNanos.length * 0.95) - 1;
        double p95Millis = elapsedNanos[p95Index] / 1_000_000.0;
        System.out.printf("opportunity-budget median=%.3fms p95=%.3fms%n", medianMillis, p95Millis);

        assertTrue(medianMillis < 2.0, "opportunity-layer median must remain below 2 ms, got " + medianMillis);
        assertTrue(p95Millis < 5.0, "opportunity-layer p95 must remain below 5 ms, got " + p95Millis);
    }

    private static PredictionContext context(EngineLimits limits, int supportCount, int attackerCount) {
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

        List<WorldSnapshot.EntitySnapshot> attackers = new ArrayList<>(attackerCount);
        for (int i = 0; i < attackerCount; i++) {
            attackers.add(new WorldSnapshot.EntitySnapshot(
                "attacker-" + i,
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
            ));
        }

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
            new WorldSnapshot(attackers, supports),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            limits,
            SafetyMode.BALANCED
        );
    }
}
