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
import dev.pixelied.survival.threat.opportunity.OpportunityTimelineAssembler;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OpportunityAlternativeBranchEngineTest {
    @Test
    void sameActorAlternativeActionsDoNotStackAsIndependentThreats() {
        PlayerSnapshot player = player();
        ThreatEvent first = threat("opportunity:bed:attacker:first", 10f, 0);
        ThreatEvent second = threat("opportunity:bed:attacker:second", 30f, 0);
        List<LethalOpportunity> alternatives = List.of(opportunity(first), opportunity(second));
        SurvivalAction.EquipDeathProtection protection = protection();
        FakeRuntime runtime = new FakeRuntime(frame(player, alternatives, List.of(protection)));
        SurvivalEngine engine = new SurvivalEngine(SurvivalConfig.defaults(), runtime, new DecisionHistory(32));

        engine.tick();

        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, runtime.started);
    }

    @Test
    void candidateMustProtectEveryAlternativeBranchNotOnlyTheFirst() {
        PlayerSnapshot player = player();
        ThreatEvent first = threat("opportunity:bed:attacker:first", 10f, 3);
        ThreatEvent second = threat("opportunity:bed:attacker:second", 30f, 3);
        List<LethalOpportunity> alternatives = List.of(opportunity(first), opportunity(second));
        SurvivalAction branchOnly = new BranchOnlyAction(first.id());
        FakeRuntime runtime = new FakeRuntime(frame(player, alternatives, List.of(branchOnly, protection())));
        SurvivalEngine engine = new SurvivalEngine(SurvivalConfig.defaults(), runtime, new DecisionHistory(32));

        engine.tick();

        assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            runtime.started,
            "an action that saves only one hypothetical branch must never outrank protection that survives every branch"
        );
    }

    @Test
    void individuallyNonlethalAlternativesDoNotFalselyArmProtectionLatch() {
        PlayerSnapshot player = player();
        ThreatEvent first = threat("opportunity:bed:attacker:first", 3f, 0);
        ThreatEvent second = threat("opportunity:bed:attacker:second", 3f, 11);
        List<LethalOpportunity> alternatives = List.of(opportunity(first), opportunity(second));
        FakeRuntime runtime = new FakeRuntime(frame(player, alternatives, List.of()));
        SurvivalEngine engine = new SurvivalEngine(SurvivalConfig.defaults(), runtime, new DecisionHistory(32));

        engine.tick();

        assertFalse(
            runtime.lastLethalWithoutProtection,
            "mutually exclusive nonlethal alternatives must not become a fake cumulative lethal latch"
        );
    }

    private static SurvivalEngine.EngineFrame frame(
        PlayerSnapshot player,
        List<LethalOpportunity> alternatives,
        List<SurvivalAction> candidates
    ) {
        ThreatTimeline actual = new ThreatTimeline(List.of());
        ThreatTimeline planning = new OpportunityTimelineAssembler().assemble(
            actual,
            alternatives,
            EngineLimits.defaults().maxThreats()
        );
        PredictionContext context = new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );
        return new SurvivalEngine.EngineFrame(context, actual, alternatives, planning, candidates);
    }

    private static SurvivalAction.EquipDeathProtection protection() {
        return new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.deterministicNoOp(),
            SurvivalAction.Hand.MAIN_HAND,
            1,
            true,
            true,
            1d,
            1,
            1
        );
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

    private static ThreatEvent threat(String id, float rawDamage, long tick) {
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
            new TickWindow(tick, tick),
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

    private record BranchOnlyAction(String removedId) implements SurvivalAction {
        @Override public int requiredServerTicks() { return 0; }
        @Override public boolean legal() { return true; }
        @Override public boolean authoritativePrerequisitesSatisfied() { return true; }
        @Override public double reliability() { return 1d; }
        @Override public int consumableCost() { return 0; }
        @Override public int disruptionCost() { return 0; }
        @Override public boolean deliberateDamage() { return false; }
        @Override public PlayerSnapshot apply(PlayerSnapshot player) { return player; }

        @Override
        public ThreatTimeline applyTimeline(ThreatTimeline timeline) {
            return new ThreatTimeline(timeline.events().stream()
                .filter(event -> !event.id().equals(removedId))
                .toList());
        }
    }

    private static final class FakeRuntime implements SurvivalEngine.RuntimeAdapter {
        private final SurvivalEngine.EngineFrame frame;
        private SurvivalAction started;
        private boolean lastLethalWithoutProtection;

        private FakeRuntime(SurvivalEngine.EngineFrame frame) {
            this.frame = frame;
        }

        @Override
        public SurvivalEngine.EngineFrame capture() {
            return frame;
        }

        @Override
        public void maintainRestoration(
            SurvivalEngine.EngineFrame ignored,
            boolean restorationEnabled,
            boolean lethalWithoutProtection,
            boolean survivalActionActive
        ) {
            lastLethalWithoutProtection = lethalWithoutProtection;
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
