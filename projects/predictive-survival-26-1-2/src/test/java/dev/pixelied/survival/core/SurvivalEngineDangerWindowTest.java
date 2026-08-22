package dev.pixelied.survival.core;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SurvivalEngineDangerWindowTest {
    @Test
    void materiallyChangedSameIdThreatCanRetryPreviouslyFailedProtection() {
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.deterministicNoOp(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1d, 1, 1
        );
        Runtime runtime = new Runtime(frame(protection, 100, 10f));
        SurvivalEngine engine = new SurvivalEngine(
            SurvivalConfig.defaults(), runtime, new DecisionHistory(128)
        );

        engine.tick();
        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, engine.currentPlan().orElseThrow().action());

        runtime.fail = true;
        runtime.frame = frame(protection, 101, 10f);
        engine.tick();
        assertEquals(1, runtime.beginCount, "failed action should be suppressed while the same danger state is unchanged");

        runtime.fail = false;
        runtime.frame = frame(protection, 102, 12f);
        engine.tick();

        assertEquals(2, runtime.beginCount,
            "a materially changed safety state for the same threat id must open a fresh retry window");
        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, engine.currentPlan().orElseThrow().action());
    }

    private static SurvivalEngine.EngineFrame frame(SurvivalAction candidate, long clientTick, float rawDamage) {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        PredictionContext context = new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(clientTick, 50, 0, new TickWindow(clientTick, clientTick)),
            EngineLimits.defaults()
        );
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage), Set.of(), false, 1f, false, Optional.empty(), "test:same"
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "same", ThreatKind.OTHER, new TickWindow(2, 2), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, false
        )));
        return new SurvivalEngine.EngineFrame(context, timeline, List.of(candidate));
    }

    private static final class Runtime implements SurvivalEngine.RuntimeAdapter {
        private SurvivalEngine.EngineFrame frame;
        private boolean fail;
        private int beginCount;

        private Runtime(SurvivalEngine.EngineFrame frame) {
            this.frame = frame;
        }

        @Override
        public SurvivalEngine.EngineFrame capture() {
            return frame;
        }

        @Override
        public ExecutionStatus begin(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            beginCount++;
            return new ExecutionStatus.WaitingForServer("sent");
        }

        @Override
        public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            return fail
                ? new ExecutionStatus.Failed("transient execution failure", true)
                : new ExecutionStatus.WaitingForServer("pending");
        }
    }
}
