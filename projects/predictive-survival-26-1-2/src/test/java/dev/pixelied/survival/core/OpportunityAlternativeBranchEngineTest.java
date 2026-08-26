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
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OpportunityAlternativeBranchEngineTest {
    @Test
    void sameActorAlternativeActionsDoNotStackAsIndependentThreats() {
        PlayerSnapshot player = player();
        ThreatEvent first = threat("opportunity:bed:attacker:first", 10f);
        ThreatEvent second = threat("opportunity:bed:attacker:second", 30f);
        List<LethalOpportunity> alternatives = List.of(opportunity(first), opportunity(second));
        ThreatTimeline union = new ThreatTimeline(List.of(first, second));
        SurvivalAction.EquipDeathProtection protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.deterministicNoOp(),
            SurvivalAction.Hand.MAIN_HAND,
            1,
            true,
            true,
            1d,
            1,
            1
        );
        PredictionContext context = new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );
        SurvivalEngine.EngineFrame frame = new SurvivalEngine.EngineFrame(
            context,
            new ThreatTimeline(List.of()),
            alternatives,
            union,
            List.of(protection)
        );
        FakeRuntime runtime = new FakeRuntime(frame);
        SurvivalEngine engine = new SurvivalEngine(SurvivalConfig.defaults(), runtime, new DecisionHistory(32));

        engine.tick();

        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, runtime.started);
    }

    private static LethalOpportunity opportunity(ThreatEvent event) {
        return new LethalOpportunity(
            event.id(),
            OpportunityFamily.BED,
            event,
            Confidence.POTENTIAL,
            2,
            Map.of("attacker_id", "attacker")
        );
    }

    private static ThreatEvent threat(String id, float rawDamage) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:alternative"
        );
        return new ThreatEvent(
            id,
            ThreatKind.OTHER,
            new TickWindow(0, 0),
            damage,
            Confidence.POTENTIAL,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        );
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(
            5f,
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
            new Vec3Snapshot(0.3, 0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }

    private static final class FakeRuntime implements SurvivalEngine.RuntimeAdapter {
        private final SurvivalEngine.EngineFrame frame;
        private SurvivalAction started;

        private FakeRuntime(SurvivalEngine.EngineFrame frame) {
            this.frame = frame;
        }

        @Override
        public SurvivalEngine.EngineFrame capture() {
            return frame;
        }

        @Override
        public ExecutionStatus begin(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            started = action;
            return new ExecutionStatus.WaitingForServer("sent");
        }

        @Override
        public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            return new ExecutionStatus.WaitingForServer("pending");
        }
    }
}
