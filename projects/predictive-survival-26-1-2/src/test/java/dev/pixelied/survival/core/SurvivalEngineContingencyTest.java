package dev.pixelied.survival.core;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.config.RescueProfile;
import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.planner.ContingencyPlan;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalEngineContingencyTest {
    @Test
    void startsShieldEvenThoughOnlyShieldThenTotemSavesWholeTimeline() {
        FakeRuntime runtime = new FakeRuntime(frame(0, arrowThenMace(), List.of(shield(), totem())));
        SurvivalEngine engine = new SurvivalEngine(config(), runtime, new DecisionHistory(32));

        engine.tick();

        assertEquals(1, runtime.beginCount);
        assertInstanceOf(SurvivalAction.RaiseShield.class, runtime.active);
        ContingencyPlan plan = engine.currentContingency().orElseThrow();
        assertTrue(plan.guaranteed());
        assertEquals(2, plan.steps().size());
        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, plan.steps().get(1).action());
        assertEquals(config().rescuePolicy(), runtime.lastPolicy);
    }

    @Test
    void threatIdentityChangeKeepsInFlightShieldAndRefreshesRemainingContingency() {
        FakeRuntime runtime = new FakeRuntime(frame(0, new ThreatTimeline(List.of(arrow())), List.of(shield(), totem())));
        SurvivalEngine engine = new SurvivalEngine(config(), runtime, new DecisionHistory(32));
        engine.tick();
        assertInstanceOf(SurvivalAction.RaiseShield.class, runtime.active);

        runtime.reportedRemainingServerTicks = 1;
        runtime.frame = frame(1, arrowThenMace(), List.of(shield(), totem()));
        engine.tick();

        assertEquals(1, runtime.beginCount, "changing threats must not restart an already-authoritative in-flight action");
        assertEquals(1, runtime.observeCount);
        assertInstanceOf(SurvivalAction.RaiseShield.class, runtime.active);
        ContingencyPlan refreshed = engine.currentContingency().orElseThrow();
        assertTrue(refreshed.guaranteed());
        assertEquals(1, refreshed.steps().getFirst().activationTick());
        assertEquals(2, refreshed.steps().size());
        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, refreshed.steps().get(1).action());
    }

    private static SurvivalConfig config() {
        return new SurvivalConfig(
            SafetyMode.SAFE,
            RescueProfile.CONSERVATIVE_SMART,
            RescuePolicy.smartDefaults(),
            true,
            false,
            false,
            false
        );
    }

    private static SurvivalAction.RaiseShield shield() {
        return new SurvivalAction.RaiseShield(
            3, true, true, true, 1d, 0f, 0, 3, 0,
            Optional.of(BlockingProfileSnapshot.fullBlock(336))
        );
    }

    private static SurvivalAction.EquipDeathProtection totem() {
        return new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.MAIN_HAND, 3, true, true, 1d, 1, 2
        );
    }

    private static SurvivalEngine.EngineFrame frame(long clientTick, ThreatTimeline timeline, List<SurvivalAction> candidates) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of(),
            Map.of("max_health", "20", "head_yaw", "0")
        );
        PredictionContext context = new PredictionContext(
            player, WorldSnapshot.empty(),
            new TimingSnapshot(clientTick, 50, 0, new TickWindow(clientTick + 1, clientTick + 1)),
            EngineLimits.defaults()
        );
        return new SurvivalEngine.EngineFrame(context, timeline, candidates);
    }

    private static ThreatTimeline arrowThenMace() {
        return new ThreatTimeline(List.of(arrow(), mace()));
    }

    private static ThreatEvent arrow() {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(100f), EnumSet.of(DamageFlag.IS_PROJECTILE), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 5)), "minecraft:arrow"
        );
        return new ThreatEvent(
            "arrow", ThreatKind.PROJECTILE, new TickWindow(6, 6), source, Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, true, false, false
        );
    }

    private static ThreatEvent mace() {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(220f), EnumSet.of(DamageFlag.BYPASSES_SHIELD), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 5)), "minecraft:mace_smash"
        );
        return new ThreatEvent(
            "mace", ThreatKind.MELEE, new TickWindow(10, 10), source, Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, false, false, false
        );
    }

    private static final class FakeRuntime implements SurvivalEngine.RuntimeAdapter {
        private SurvivalEngine.EngineFrame frame;
        private SurvivalAction active;
        private int beginCount;
        private int observeCount;
        private int reportedRemainingServerTicks = -1;
        private RescuePolicy lastPolicy;

        private FakeRuntime(SurvivalEngine.EngineFrame frame) {
            this.frame = frame;
        }

        @Override
        public SurvivalEngine.EngineFrame capture() {
            return frame;
        }

        @Override
        public SurvivalEngine.EngineFrame capture(RescuePolicy policy) {
            lastPolicy = policy;
            return frame;
        }

        @Override
        public ExecutionStatus begin(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            beginCount++;
            active = action;
            return new ExecutionStatus.WaitingForServer("sent");
        }

        @Override
        public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            observeCount++;
            return new ExecutionStatus.WaitingForServer("pending");
        }

        @Override
        public int remainingServerTicks(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            return reportedRemainingServerTicks >= 0 ? reportedRemainingServerTicks : action.requiredServerTicks();
        }
    }
}
