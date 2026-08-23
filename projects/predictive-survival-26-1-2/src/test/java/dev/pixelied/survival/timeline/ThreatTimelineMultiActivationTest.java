package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.planner.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatTimelineMultiActivationTest {
    @Test
    void appliesEachRescueOnlyWhenItsOwnActivationBecomesAuthoritative() {
        ThreatTimelineSimulator simulator = new ThreatTimelineSimulator();
        PlayerSnapshot start = player();
        ThreatTimeline timeline = timeline();
        DeathProtectionSnapshot.ProtectionItem totem = DeathProtectionSnapshot.ProtectionItem.vanillaTotem();
        SurvivalAction.EquipDeathProtection first = new SurvivalAction.EquipDeathProtection(
            totem, SurvivalAction.Hand.OFF_HAND, 0, true, true, 1d, 1, 1
        );
        SurvivalAction.EquipDeathProtection second = new SurvivalAction.EquipDeathProtection(
            totem, SurvivalAction.Hand.MAIN_HAND, 0, true, true, 1d, 1, 1
        );

        TimelineResult oneActivation = simulator.simulateWithActivation(start, timeline, 2, first::apply);
        assertFalse(oneActivation.survived(), "one protection item must not cover both lethal hits");

        TimelineResult result = simulator.simulateWithActivations(start, timeline, List.of(
            new ThreatTimelineSimulator.TimedActivation(2, first::apply),
            new ThreatTimelineSimulator.TimedActivation(4, second::apply)
        ));

        assertTrue(result.survived());
        assertEquals(2, result.consumedDeathProtectionCount());
    }

    @Test
    void uncertainHitThatCouldBeatActivationIsForcedBeforeIt() {
        ThreatTimelineSimulator simulator = new ThreatTimelineSimulator();
        SurvivalAction.EquipDeathProtection protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND, 0, true, true, 1d, 1, 1
        );
        DamageSourceSnapshot lethal = damage(100f, "test:window");
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "window", ThreatKind.OTHER, new TickWindow(1, 3), lethal, Confidence.BOUNDED,
            Optional.empty(), Optional.empty(), false, false, false, false
        )));

        TimelineResult result = simulator.simulateWithActivations(player(), timeline, List.of(
            new ThreatTimelineSimulator.TimedActivation(2, protection::apply)
        ));

        assertFalse(result.survived(), "an impact window that begins before activation cannot be credited the rescue");
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of("max_health", "20")
        );
    }

    private static ThreatTimeline timeline() {
        return new ThreatTimeline(List.of(
            new ThreatEvent(
                "first", ThreatKind.PROJECTILE, new TickWindow(3, 3), damage(100f, "test:arrow"), Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            ),
            new ThreatEvent(
                "second", ThreatKind.MELEE, new TickWindow(5, 5), damage(220f, "test:mace"), Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            )
        ));
    }

    private static DamageSourceSnapshot damage(float amount, String key) {
        return new DamageSourceSnapshot(
            DamageRange.exact(amount), Set.of(), false, 1f, false, Optional.empty(), key
        );
    }
}
