package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescueProfile;
import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ContingencyPlannerSourceReuseTest {
    @Test
    void onePhysicalRoutedStackCannotBeCreditedAsTwoDifferentRescueActions() {
        PredictionContext context = context();
        ThreatTimeline timeline = lethalTimeline();
        SurvivalAction.HeldItemRef sharedSource = new SurvivalAction.HeldItemRef(
            SurvivalAction.Hand.MAIN_HAND,
            "test:multi_capability_rescue",
            12345,
            Optional.of(new SurvivalItemRoute.HotbarSelect(
                2,
                SurvivalAction.Hand.MAIN_HAND,
                "test:multi_capability_rescue",
                12345
            ))
        );

        SurvivalAction.ApplyEffects firstUse = healingAction(4f, 1, sharedSource);
        SurvivalAction.ApplyEffects secondUse = healingAction(5f, 2, sharedSource);

        ContingencyPlan plan = new ContingencyPlanner().plan(
            context,
            timeline,
            List.of(firstUse, secondUse),
            SafetyMode.SAFE,
            RescueProfile.SMART
        );

        assertFalse(
            plan.guaranteed(),
            "one exact inventory stack must not be simulated as two independently available rescue resources"
        );
    }

    private static SurvivalAction.ApplyEffects healingAction(
        float healthGain,
        int disruptionCost,
        SurvivalAction.HeldItemRef source
    ) {
        return new SurvivalAction.ApplyEffects(
            StatusEffectsSnapshot.none(),
            healthGain,
            0f,
            "test:multi_capability_rescue",
            1,
            true,
            true,
            1d,
            1,
            disruptionCost,
            Optional.of(source),
            List.of(),
            -1f
        );
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            4f,
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
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            Map.of("max_health", "20", "head_yaw", "0")
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)),
            EngineLimits.defaults()
        );
    }

    private static ThreatTimeline lethalTimeline() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "minecraft:generic"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "late-lethal-hit",
            ThreatKind.OTHER,
            new TickWindow(20, 20),
            damage,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        )));
    }
}
