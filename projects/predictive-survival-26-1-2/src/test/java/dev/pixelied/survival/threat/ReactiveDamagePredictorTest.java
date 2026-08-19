package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveDamagePredictorTest {
    private final ReactiveDamagePredictor predictor = new ReactiveDamagePredictor();

    @Test
    void outgoingAttackCanEmitBoundedThornsThreat() {
        WorldSnapshot.EntitySnapshot target = entity(
            "target:7",
            "minecraft:player",
            Map.of("thorns_levels", "3,2")
        );
        List<ThreatEvent> events = predictor.predict(context(
            Map.of("outgoing_attack_target_id", "target:7"),
            List.of(target)
        ));

        assertEquals(2, events.size());
        for (ThreatEvent event : events) {
            assertEquals(Confidence.BOUNDED, event.confidence());
            assertEquals("minecraft:thorns", event.damage().sourceKey());
            assertEquals(0f, event.damage().rawDamage().min(), 0.0001f);
            assertEquals(5f, event.damage().rawDamage().max(), 0.0001f);
            assertEquals(new TickWindow(0, 0), event.impact());
            assertTrue(event.blockable());
        }
    }

    @Test
    void ownPendingPearlPredictsFiveRawDamageAtTeleport() {
        WorldSnapshot.EntitySnapshot pearl = entity(
            "pearl:1",
            "minecraft:ender_pearl",
            Map.of("owner_is_local_player", "true", "predicted_impact_tick", "6")
        );
        ThreatEvent event = predictor.predict(context(Map.of(), List.of(pearl))).getFirst();

        assertEquals(new TickWindow(6, 6), event.impact());
        assertEquals(5f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(5f, event.damage().rawDamage().max(), 0.0001f);
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().flags().contains(DamageFlag.IS_FALL));
    }

    @Test
    void ownPearlWithoutExactImpactUsesConservativeProjectileHorizon() {
        WorldSnapshot.EntitySnapshot pearl = entity(
            "pearl:2",
            "minecraft:ender_pearl",
            Map.of("owner_is_local_player", "true")
        );

        ThreatEvent event = predictor.predict(context(Map.of(), List.of(pearl))).getFirst();

        assertEquals(new TickWindow(1, EngineLimits.defaults().maxProjectileHorizonTicks()), event.impact());
        assertEquals(Confidence.BOUNDED, event.confidence());
        assertEquals(5f, event.damage().rawDamage().max(), 0.0001f);
    }

    @Test
    void visibleThornsArmorWithoutOutgoingAttackProducesNoThreat() {
        WorldSnapshot.EntitySnapshot target = entity(
            "target:8",
            "minecraft:player",
            Map.of("thorns_levels", "3,3,3,3")
        );

        assertTrue(predictor.predict(context(Map.of(), List.of(target))).isEmpty());
    }

    private static PredictionContext context(
        Map<String, String> playerState,
        List<WorldSnapshot.EntitySnapshot> entities
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
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
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            playerState
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot entity(
        String id,
        String type,
        Map<String, String> properties
    ) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            type,
            new Vec3Snapshot(2, 1, 0),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(1.8, 0.8, -0.2, 2.2, 1.2, 0.2),
            properties
        );
    }
}
