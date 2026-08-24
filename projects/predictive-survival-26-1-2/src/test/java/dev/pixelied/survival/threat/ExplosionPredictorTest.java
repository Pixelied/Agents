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
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.pixelied.survival.damage.DamageFlag.IS_EXPLOSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionPredictorTest {
    @Test
    void tntFuseProducesExactImpactTick() {
        WorldSnapshot.EntitySnapshot tnt = entity("tnt:1", "minecraft:tnt", Map.of(
            "explosion_radius", "4.0",
            "fuse_ticks", "80"
        ));

        ThreatEvent event = new ExplosionPredictor().predict(context(List.of(tnt))).getFirst();
        assertEquals(new TickWindow(80, 80), event.impact());
        assertEquals(Confidence.EXACT, event.confidence());
        assertTrue(event.damage().flags().contains(IS_EXPLOSION));
    }

    @Test
    void tntMinecartCanCarryBoundedFuseAndExplosionRadius() {
        WorldSnapshot.EntitySnapshot minecart = entity("minecart:1", "minecraft:tnt_minecart", Map.of(
            "explosion_radius_min", "4.0",
            "explosion_radius_max", "11.5",
            "fuse_ticks_min", "0",
            "fuse_ticks_max", "73"
        ));

        ThreatEvent event = new ExplosionPredictor().predict(context(List.of(minecart))).getFirst();
        assertEquals(new TickWindow(0, 73), event.impact());
        assertEquals(Confidence.BOUNDED, event.confidence());
        assertTrue(event.damage().rawDamage().max() > event.damage().rawDamage().min());
        assertTrue(event.damage().flags().contains(IS_EXPLOSION));
    }

    @Test
    void triggerableCrystalWithoutFuseIsPotentialImmediateThreat() {
        WorldSnapshot.EntitySnapshot crystal = entity("crystal:7", "minecraft:end_crystal", Map.of(
            "explosion_radius", "6.0",
            "triggerable", "true"
        ));

        ThreatEvent event = new ExplosionPredictor().predict(context(List.of(crystal))).getFirst();
        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertEquals(new TickWindow(0, 2), event.impact());
    }

    @Test
    void triggerableThreatWindowExtendsThroughLatestServerProcessingTick() {
        WorldSnapshot.EntitySnapshot crystal = entity("crystal:timing", "minecraft:end_crystal", Map.of(
            "explosion_radius", "6.0",
            "triggerable", "true"
        ));

        ThreatEvent event = new ExplosionPredictor().predict(context(
            List.of(crystal), List.of(), player(new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(0, 0, 0)),
            new TimingSnapshot(0, 350, 50, new TickWindow(2, 6))
        )).getFirst();

        assertEquals(new TickWindow(0, 6), event.impact());
        assertEquals(Confidence.POTENTIAL, event.confidence());
    }

    @Test
    void movementIntoTriggerableCrystalEnvelopeRaisesConservativeDamageBound() {
        WorldSnapshot.EntitySnapshot crystal = new WorldSnapshot.EntitySnapshot(
            "crystal:moving", "minecraft:end_crystal", new Vec3Snapshot(10.3, 0, 0.3), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(10, 0, 0, 10.6, 2, 0.6),
            Map.of("explosion_radius", "6.0", "triggerable", "true")
        );
        TimingSnapshot slowAuthority = new TimingSnapshot(0, 350, 50, new TickWindow(2, 6));

        ThreatEvent stationary = new ExplosionPredictor().predict(context(
            List.of(crystal), List.of(), player(new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(0, 0, 0)), slowAuthority
        )).getFirst();
        ThreatEvent movingToward = new ExplosionPredictor().predict(context(
            List.of(crystal), List.of(), player(new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(1.4, 0, 0)), slowAuthority
        )).getFirst();

        assertTrue(stationary.damage().rawDamage().max() < 10f);
        assertTrue(movingToward.damage().rawDamage().max() > 20f);
    }

    @Test
    void removedTriggerableSourceBlockCannotShieldItsOwnExplosion() {
        WorldSnapshot.BlockSnapshot anchor = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5), "minecraft:respawn_anchor", true, Map.of(
                "full_collision_cube", "true",
                "explosion_radius", "5.0",
                "triggerable", "true",
                "source_key", "minecraft:bad_respawn_point",
                "pre_explosion_remove_group", "anchor:1,0,0"
            )
        );

        ThreatEvent event = new ExplosionPredictor().predict(context(List.of(), List.of(anchor))).getFirst();

        assertTrue(event.damage().rawDamage().max() > 20f);
    }

    @Test
    void bothBedHalvesAreExcludedBeforeHeadExplosionExposure() {
        Map<String, String> group = Map.of(
            "full_collision_cube", "true",
            "pre_explosion_remove_group", "bed:2,0,0"
        );
        WorldSnapshot.BlockSnapshot foot = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5), "minecraft:red_bed", true, group
        );
        WorldSnapshot.BlockSnapshot head = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(2.5, 0.5, 0.5), "minecraft:red_bed", true, Map.of(
                "full_collision_cube", "true",
                "pre_explosion_remove_group", "bed:2,0,0",
                "explosion_radius", "5.0",
                "triggerable", "true",
                "source_key", "minecraft:bad_respawn_point"
            )
        );

        List<ThreatEvent> events = new ExplosionPredictor().predict(context(List.of(), List.of(foot, head)));

        assertEquals(1, events.size());
        assertTrue(events.getFirst().damage().rawDamage().max() > 20f);
    }

    @Test
    void unknownCollisionShapeIsNeverAssumedToBeAFullCube() {
        WorldSnapshot.BlockSnapshot partialOrUnknown = block(true, Map.of());
        WorldSnapshot.BlockSnapshot confirmedFullCube = block(true, Map.of("full_collision_cube", "true"));

        assertFalse(ExplosionPredictor.canUseUnitCubeOcclusion(partialOrUnknown));
        assertTrue(ExplosionPredictor.canUseUnitCubeOcclusion(confirmedFullCube));
    }

    @Test
    void groundFaceTouchMovingAwayDoesNotCreateFakeExplosionCover() {
        WorldSnapshot.EntitySnapshot tnt = new WorldSnapshot.EntitySnapshot(
            "tnt:ground-boundary",
            "minecraft:tnt",
            new Vec3Snapshot(0.3, 0.9, 6.5),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(0.25, 0.85, 6.45, 0.35, 0.95, 6.55),
            Map.of("explosion_radius", "4.0", "fuse_ticks", "1")
        );
        WorldSnapshot.BlockSnapshot ground = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(0.5, -0.5, 0.5),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        float openRaw = new ExplosionPredictor().predict(context(List.of(tnt), List.of())).getFirst()
            .damage().rawDamage().max();
        float groundedRaw = new ExplosionPredictor().predict(context(List.of(tnt), List.of(ground))).getFirst()
            .damage().rawDamage().max();

        assertEquals(openRaw, groundedRaw, 0.000001f);
    }

    private static PredictionContext context(List<WorldSnapshot.EntitySnapshot> entities) {
        return context(entities, List.of());
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        return context(
            entities, blocks,
            player(new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(0, 0, 0)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2))
        );
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks,
        PlayerSnapshot player,
        TimingSnapshot timing
    ) {
        return new PredictionContext(player, new WorldSnapshot(entities, blocks), timing, EngineLimits.defaults());
    }

    private static PlayerSnapshot player(Vec3Snapshot position, Vec3Snapshot velocity) {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(position.x() - 0.3, position.y(), position.z() - 0.3,
                position.x() + 0.3, position.y() + 1.8, position.z() + 0.3),
            position, velocity, Map.of()
        );
    }

    private static WorldSnapshot.EntitySnapshot entity(String id, String type, Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            id, type, new Vec3Snapshot(3, 0, 0), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(3, 0, 0, 4, 1, 1), properties
        );
    }

    private static WorldSnapshot.BlockSnapshot block(boolean collision, Map<String, String> properties) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1, 0, 0), "minecraft:test_block", collision, properties
        );
    }
}
