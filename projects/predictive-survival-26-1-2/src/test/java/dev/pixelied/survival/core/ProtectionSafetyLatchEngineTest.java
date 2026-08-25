package dev.pixelied.survival.core;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionSafetyLatchEngineTest {
    @Test
    void planningOnlyLethalRiskFiltersShieldThatWouldReplaceOnlyTotemHand() {
        PlayerSnapshot player = protectedPlayer();
        ThreatTimeline planning = doubleLethalTimeline();
        SurvivalAction.RaiseShield unsafeShield = routedMainHandShield();
        SurvivalAction.EquipDeathProtection secondTotem = secondTotem();
        SurvivalEngine.EngineFrame frame = frame(player, planning, List.of(unsafeShield, secondTotem));
        FakeRuntime runtime = new FakeRuntime(frame);
        SurvivalEngine engine = new SurvivalEngine(SurvivalConfig.defaults(), runtime, new DecisionHistory(32));

        engine.tick();

        assertTrue(runtime.maintenanceCalled);
        assertTrue(runtime.lastLethalWithoutProtection, "planning-only lethal risk must latch restoration too");
        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, runtime.startedAction);
        assertEquals(secondTotem, runtime.startedAction);
    }

    private static SurvivalEngine.EngineFrame frame(
        PlayerSnapshot player,
        ThreatTimeline planning,
        List<SurvivalAction> candidates
    ) {
        PredictionContext context = new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );
        return new SurvivalEngine.EngineFrame(
            context,
            new ThreatTimeline(List.of()),
            List.of(),
            planning,
            candidates
        );
    }

    private static ThreatTimeline doubleLethalTimeline() {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(10f),
            Set.of(DamageFlag.BYPASSES_COOLDOWN),
            false,
            1f,
            false,
            Optional.empty(),
            "test:burst"
        );
        ThreatEvent first = new ThreatEvent(
            "planning:first",
            ThreatKind.OTHER,
            new TickWindow(2, 2),
            source,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            true,
            true,
            true,
            false
        );
        ThreatEvent second = new ThreatEvent(
            "planning:second",
            ThreatKind.OTHER,
            new TickWindow(3, 3),
            source,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            true,
            true,
            true,
            false
        );
        return new ThreatTimeline(List.of(first, second));
    }

    private static PlayerSnapshot protectedPlayer() {
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
            DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.deterministicNoOp()),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            Map.of("main_hand", "minecraft:totem_of_undying", "off_hand", "minecraft:air")
        );
    }

    private static SurvivalAction.RaiseShield routedMainHandShield() {
        SurvivalItemRoute route = new SurvivalItemRoute.HotbarSelect(
            1,
            SurvivalAction.Hand.MAIN_HAND,
            "minecraft:shield",
            17
        );
        SurvivalAction.HeldItemRef source = new SurvivalAction.HeldItemRef(
            SurvivalAction.Hand.MAIN_HAND,
            "minecraft:shield",
            17,
            Optional.of(route)
        );
        return new SurvivalAction.RaiseShield(
            1,
            true,
            true,
            true,
            1d,
            1f,
            0,
            0,
            0,
            Optional.empty(),
            Optional.of(source)
        );
    }

    private static SurvivalAction.EquipDeathProtection secondTotem() {
        return new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.deterministicNoOp(),
            SurvivalAction.Hand.OFF_HAND,
            1,
            true,
            true,
            1d,
            1,
            10
        );
    }

    private static final class FakeRuntime implements SurvivalEngine.RuntimeAdapter {
        private final SurvivalEngine.EngineFrame frame;
        private SurvivalAction startedAction;
        private boolean maintenanceCalled;
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
            maintenanceCalled = true;
            lastLethalWithoutProtection = lethalWithoutProtection;
        }

        @Override
        public ExecutionStatus begin(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            startedAction = action;
            return new ExecutionStatus.WaitingForServer("sent");
        }

        @Override
        public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame ignored) {
            return new ExecutionStatus.WaitingForServer("pending");
        }
    }
}
