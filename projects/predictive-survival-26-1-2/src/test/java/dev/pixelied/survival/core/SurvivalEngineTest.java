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
import dev.pixelied.survival.planner.SurvivalPlan;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SurvivalEngineTest {
    @Test
    void engineEscalatesWhenShieldExecutionMissesDeadline() {
        SurvivalAction shield = new SurvivalAction.RaiseShield(
            0, true, true, true, 1d, 1f, 5, 5, 0
        );
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.generic(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1d, 1, 1
        );
        FakeRuntime runtime = new FakeRuntime(frame(List.of(shield, protection)));
        SurvivalEngine engine = new SurvivalEngine(
            SurvivalConfig.defaults(),
            runtime,
            new DecisionHistory(128)
        );

        engine.tick();
        assertInstanceOf(SurvivalAction.RaiseShield.class, engine.currentPlan().orElseThrow().action());

        runtime.failObservedAction = true;
        engine.tick();

        SurvivalPlan escalated = engine.currentPlan().orElseThrow();
        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, escalated.action());
    }

    @Test
    void defaultConfigFiltersAutomaticMovementCandidates() {
        SurvivalAction relocate = new SurvivalAction.Relocate(
            new Vec3Snapshot(4, 0, 0), Set.of("incoming"),
            0, true, true, 1d, 0, 0
        );
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.generic(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1d, 1, 1
        );
        FakeRuntime runtime = new FakeRuntime(frame(List.of(relocate, protection)));
        SurvivalEngine engine = new SurvivalEngine(
            SurvivalConfig.defaults(), runtime, new DecisionHistory(128)
        );

        engine.tick();

        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, engine.currentPlan().orElseThrow().action());
    }

    private static SurvivalEngine.EngineFrame frame(List<SurvivalAction> candidates) {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        PredictionContext context = new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:incoming"
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "incoming", ThreatKind.OTHER, new TickWindow(1, 1), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, false
        )));
        return new SurvivalEngine.EngineFrame(context, timeline, candidates);
    }

    private static final class FakeRuntime implements SurvivalEngine.RuntimeAdapter {
        private final SurvivalEngine.EngineFrame frame;
        private SurvivalAction active;
        private boolean failObservedAction;

        private FakeRuntime(SurvivalEngine.EngineFrame frame) {
            this.frame = frame;
        }

        @Override
        public SurvivalEngine.EngineFrame capture() {
            return frame;
        }

        @Override
        public ExecutionStatus begin(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            active = action;
            return new ExecutionStatus.WaitingForServer("sent");
        }

        @Override
        public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            if (failObservedAction && action.equals(active)) {
                return new ExecutionStatus.Failed("deadline missed", true);
            }
            return new ExecutionStatus.WaitingForServer("pending");
        }
    }
}
