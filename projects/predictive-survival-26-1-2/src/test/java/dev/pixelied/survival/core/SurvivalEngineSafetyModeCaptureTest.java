package dev.pixelied.survival.core;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurvivalEngineSafetyModeCaptureTest {
    @Test
    void enginePassesConfiguredSafetyModeIntoRuntimeCapture() {
        CapturingRuntime runtime = new CapturingRuntime(frame());
        SurvivalEngine engine = new SurvivalEngine(
            SurvivalConfig.defaults(), runtime, new DecisionHistory(16)
        );

        engine.tick();

        assertEquals(SafetyMode.SAFE, runtime.capturedSafetyMode);
    }

    private static SurvivalEngine.EngineFrame frame() {
        PredictionContext context = new PredictionContext(
            player(), WorldSnapshot.empty(), new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)), EngineLimits.defaults()
        );
        return new SurvivalEngine.EngineFrame(context, new ThreatTimeline(List.of()), List.of());
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }

    private static final class CapturingRuntime implements SurvivalEngine.RuntimeAdapter {
        private final SurvivalEngine.EngineFrame frame;
        private SafetyMode capturedSafetyMode;

        private CapturingRuntime(SurvivalEngine.EngineFrame frame) {
            this.frame = frame;
        }

        @Override
        public SurvivalEngine.EngineFrame capture() {
            throw new AssertionError("engine used legacy capture path");
        }

        public SurvivalEngine.EngineFrame capture(RescuePolicy policy, SafetyMode safetyMode) {
            capturedSafetyMode = safetyMode;
            return frame;
        }

        @Override
        public ExecutionStatus begin(SurvivalAction action, SurvivalEngine.EngineFrame frame) {
            throw new AssertionError("safe empty frame must not dispatch an action");
        }

        @Override
        public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame frame) {
            throw new AssertionError("safe empty frame has no active action");
        }
    }
}
