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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CausalThreatTimelineTest {
    private final ThreatTimelineSimulator simulator = new ThreatTimelineSimulator();

    @Test
    void explosionOccurrenceRemovesLaterCrystalSourceEvenWhenPlayerDamageIsRejected() {
        PlayerSnapshot start = playerWithHurtState(
            5f,
            new HurtState(DamageRange.exact(5f), 20, Confidence.EXACT)
        );
        ThreatEvent crystalA = event("explosion:101", 4f, 0);
        ThreatEvent crystalB = event("explosion:102", 10f, 21);
        ThreatTimeline flat = new ThreatTimeline(List.of(crystalA, crystalB));

        TimelineResult flatResult = simulator.simulate(start, flat);
        assertFalse(flatResult.survived(), "flat simulation must demonstrate the stale second-crystal failure");
        assertEquals("explosion:102", flatResult.firstLethalEventId().orElseThrow());
        assertTrue(flatResult.eventResult("explosion:101").damageResult().rejected());

        CausalThreatTimeline causal = new CausalThreatTimeline(
            flat,
            Map.of(
                crystalA.id(), "entity:101",
                crystalB.id(), "entity:102"
            ),
            Map.of(
                crystalA.id(), List.of(new ThreatTransition.RemoveSource("entity:102"))
            )
        );

        TimelineResult causalResult = simulator.simulate(start, causal);

        assertTrue(causalResult.survived());
        assertEquals(1, causalResult.eventResults().size());
        assertEquals("explosion:101", causalResult.eventResults().getFirst().event().id());
        assertTrue(causalResult.eventResults().getFirst().damageResult().rejected());
    }

    @Test
    void mutuallyDestructiveSameTickCrystalsCannotConsumeTwoProtections() {
        PlayerSnapshot start = playerWithProtection(5f);
        ThreatEvent crystalA = event("explosion:201", 10f, 0);
        ThreatEvent crystalB = event("explosion:202", 20f, 0);
        ThreatTimeline flat = new ThreatTimeline(List.of(crystalA, crystalB));

        TimelineResult flatResult = simulator.simulate(start, flat);
        assertFalse(flatResult.survived(),
            "flat simulation must expose the impossible A-then-B differential-damage branch");
        assertEquals(1, flatResult.consumedDeathProtectionCount());

        CausalThreatTimeline causal = new CausalThreatTimeline(
            flat,
            Map.of(
                crystalA.id(), "entity:201",
                crystalB.id(), "entity:202"
            ),
            Map.of(
                crystalA.id(), List.of(new ThreatTransition.RemoveSource("entity:202")),
                crystalB.id(), List.of(new ThreatTransition.RemoveSource("entity:201"))
            )
        );

        TimelineResult causalResult = simulator.simulate(start, causal);

        assertTrue(causalResult.survived(),
            "whichever crystal explodes first removes the other source before its own detonation");
        assertEquals(1, causalResult.eventResults().size());
        assertEquals(1, causalResult.consumedDeathProtectionCount());
        assertTrue(causalResult.finalHealth() > 0f);
    }

    private static ThreatEvent event(String id, float rawDamage, long tick) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:" + id
        );
        return new ThreatEvent(
            id,
            ThreatKind.EXPLOSION,
            new TickWindow(tick, tick),
            damage,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
    }

    private static PlayerSnapshot playerWithProtection(float health) {
        return new PlayerSnapshot(
            health,
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
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }

    private static PlayerSnapshot playerWithHurtState(float health, HurtState hurtState) {
        return new PlayerSnapshot(
            health,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            hurtState,
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }
}
