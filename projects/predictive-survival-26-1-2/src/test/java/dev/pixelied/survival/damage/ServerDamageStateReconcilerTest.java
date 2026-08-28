package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerDamageStateReconcilerTest {
    private final ServerDamageStateReconciler reconciler = new ServerDamageStateReconciler();

    @Test
    void fullAcceptedHitStoresPreArmorComparisonAmountInsteadOfObservedHealthDelta() {
        PlayerSnapshot before = player(20f, 5f, HurtState.unknown());
        ThreatEvent hit = event("full", "minecraft:player_attack", 8f);

        HurtState reconciled = reconciler.reconcile(
            before,
            List.of(hit),
            17f,
            0f,
            List.of(new ServerDamageStateReconciler.DamageEventObservation(
                "minecraft:player_attack",
                new TickWindow(0, 0)
            ))
        );

        assertEquals(DamageRange.exact(8f), reconciled.lastHurt());
        assertEquals(20, reconciled.invulnerableTime());
        assertEquals(Confidence.MATCHED, reconciled.confidence());
    }

    @Test
    void equalSecondHitInsideCooldownKeepsPriorTrustedStateWhenNoDamageWasAccepted() {
        HurtState prior = new HurtState(DamageRange.exact(10f), 19, Confidence.MATCHED);
        PlayerSnapshot before = player(20f, 0f, prior);

        HurtState reconciled = reconciler.reconcile(
            before,
            List.of(event("equal", "minecraft:player_attack", 10f)),
            20f,
            0f,
            List.of()
        );

        assertEquals(prior, reconciled);
    }

    @Test
    void weakerSecondHitInsideCooldownKeepsPriorTrustedStateWhenNoDamageWasAccepted() {
        HurtState prior = new HurtState(DamageRange.exact(10f), 19, Confidence.MATCHED);
        PlayerSnapshot before = player(20f, 0f, prior);

        HurtState reconciled = reconciler.reconcile(
            before,
            List.of(event("weaker", "minecraft:player_attack", 8f)),
            20f,
            0f,
            List.of()
        );

        assertEquals(prior, reconciled);
    }

    @Test
    void strongerDifferentialHitReconcilesWithoutDamageEventPacket() {
        PlayerSnapshot before = player(
            20f,
            0f,
            new HurtState(DamageRange.exact(10f), 19, Confidence.MATCHED)
        );

        HurtState reconciled = reconciler.reconcile(
            before,
            List.of(event("stronger", "minecraft:player_attack", 15f)),
            15f,
            0f,
            List.of()
        );

        assertEquals(DamageRange.exact(15f), reconciled.lastHurt());
        assertEquals(19, reconciled.invulnerableTime(),
            "vanilla differential damage updates lastHurt but does not reset the existing cooldown to 20");
        assertEquals(Confidence.MATCHED, reconciled.confidence());
    }

    @Test
    void overlappingCompatibleCandidatesInvalidateInsteadOfGuessingLastHurt() {
        PlayerSnapshot before = player(
            20f,
            0f,
            new HurtState(DamageRange.exact(10f), 19, Confidence.MATCHED)
        );

        HurtState reconciled = reconciler.reconcile(
            before,
            List.of(
                event("first", "minecraft:player_attack", 15f),
                event("second", "minecraft:mob_attack", 15f)
            ),
            15f,
            0f,
            List.of()
        );

        assertEquals(HurtState.unknown(), reconciled,
            "without a full-damage event packet the same observed differential can map to two source identities");
    }

    private static PlayerSnapshot player(float health, float absorption, HurtState hurtState) {
        return new PlayerSnapshot(
            health,
            absorption,
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

    private static ThreatEvent event(String id, String sourceKey, float rawDamage) {
        return new ThreatEvent(
            id,
            ThreatKind.MELEE,
            new TickWindow(0, 0),
            new DamageSourceSnapshot(
                DamageRange.exact(rawDamage),
                Set.of(),
                false,
                1f,
                false,
                Optional.empty(),
                sourceKey
            ),
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
    }
}
