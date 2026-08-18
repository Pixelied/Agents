package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
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

class ProjectilePredictorTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void arrowReportsFirstSweptPlayerIntersection() {
        List<ThreatEvent> events = predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0", "critical", "false"))),
            List.of()
        ));

        assertEquals(new TickWindow(7, 7), events.getFirst().impact());
        assertEquals(DamageRange.exact(6f), events.getFirst().damage().rawDamage());
    }

    @Test
    void oneTickObservedServerLeadPullsImpactForwardWithoutChangingDamage() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of(
                "base_damage", "6.0",
                "critical", "false",
                "observation_age_ticks", "1"
            ))),
            List.of()
        )).getFirst();

        assertEquals(new TickWindow(6, 6), event.impact());
        assertEquals(DamageRange.exact(6f), event.damage().rawDamage());
    }

    @Test
    void tridentExplicitRawDamageOverridesArrowLikeVelocityFormula() {
        WorldSnapshot.EntitySnapshot trident = new WorldSnapshot.EntitySnapshot(
            "trident:1",
            "minecraft:trident",
            new Vec3Snapshot(0, 1.9, 0.3),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(-0.125, 1.775, 0.175, 0.125, 2.025, 0.425),
            Map.of(
                "base_damage", "2.0",
                "raw_damage", "8.0",
                "critical", "false"
            )
        );

        ThreatEvent event = predictor.predict(context(List.of(trident), List.of())).getFirst();
        assertEquals(DamageRange.exact(8f), event.damage().rawDamage());
    }

    @Test
    void earlierWallCollisionRemovesPlayerThreat() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(4, 1, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        assertTrue(predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0", "critical", "false"))),
            List.of(wall)
        )).isEmpty());
    }

    @Test
    void unknownCriticalStateWidensDamageRange() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0", "critical", "unknown"))),
            List.of()
        )).getFirst();

        assertEquals(6f, event.damage().rawDamage().min(), 0.0001f);
        assertTrue(event.damage().rawDamage().max() > event.damage().rawDamage().min());
    }

    @Test
    void missingCriticalStateIsTreatedAsUnknown() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0"))),
            List.of()
        )).getFirst();

        assertEquals(6f, event.damage().rawDamage().min(), 0.0001f);
        assertTrue(event.damage().rawDamage().max() > event.damage().rawDamage().min());
    }

    @Test
    void missingBaseDamageNeverFallsBackToVanillaMinimum() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of("critical", "false"))),
            List.of()
        )).getFirst();

        assertEquals(Float.MAX_VALUE, event.damage().rawDamage().max());
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, blocks),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot arrow(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "arrow:1",
            "minecraft:arrow",
            new Vec3Snapshot(0, 1.9, 0.3),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(-0.125, 1.775, 0.175, 0.125, 2.025, 0.425),
            properties
        );
    }
}
