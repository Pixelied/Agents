package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostImpactHorizonPredictorTest {
    @Test
    void directSplashStatusKeepsLatePulseAfterProjectileHorizon() {
        List<ThreatEvent> poison = new ProjectilePredictor().predict(context(
            splash(Map.of(
                "potion_poison_duration_ticks", "125",
                "potion_poison_amplifier", "0",
                "potion_duration_scale", "1.0"
            )),
            List.of(),
            EngineLimits.defaults()
        )).stream().filter(event -> event.id().contains(":poison:")).toList();

        assertTrue(
            poison.stream().anyMatch(event -> event.impact().equals(new TickWindow(105, 105))),
            "impact is found inside the 80-tick trajectory horizon, so the resulting poison pulse at tick 105 must survive inside the 128-tick decision horizon"
        );
    }

    @Test
    void wallSplashStatusKeepsLatePulseAfterProjectileHorizon() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(5, 1, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );
        List<ThreatEvent> wither = EnvironmentPredictorRegistry.defaults().predict(context(
            splash(Map.of(
                "potion_wither_duration_ticks", "240",
                "potion_wither_amplifier", "0",
                "potion_duration_scale", "1.0",
                "potion_splash_radius", "4.0"
            )),
            List.of(wall),
            EngineLimits.defaults()
        )).stream().filter(event -> event.id().contains(":splash_status:wither:")).toList();

        assertTrue(
            wither.stream().anyMatch(event -> event.impact().earliest() > 80 && event.impact().latest() <= 128),
            "wall splash status consequences inside the decision horizon must not be clipped by trajectory time"
        );
    }

    @Test
    void lingeringStatusKeepsLatePulseAfterProjectileHorizon() {
        List<ThreatEvent> wither = EnvironmentPredictorRegistry.defaults().predict(context(
            lingering(Map.of(
                "potion_wither_duration_ticks", "480",
                "potion_wither_amplifier", "0",
                "potion_duration_scale", "0.25",
                "potion_lingering", "true"
            )),
            List.of(),
            EngineLimits.defaults()
        )).stream().filter(event -> event.id().contains(":lingering_status:wither:")).toList();

        assertTrue(
            wither.stream().anyMatch(event -> event.impact().equals(new TickWindow(95, 95))),
            "lingering Wither applied at tick 15 must retain its tick-95 pulse inside the decision horizon"
        );
    }

    @Test
    void directSplashStatusStopsAtDecisionHorizon() {
        EngineLimits limits = new EngineLimits(128, 32, 80, 60);
        List<ThreatEvent> poison = new ProjectilePredictor().predict(context(
            splash(Map.of(
                "potion_poison_duration_ticks", "125",
                "potion_poison_amplifier", "0",
                "potion_duration_scale", "1.0"
            )),
            List.of(),
            limits
        )).stream().filter(event -> event.id().contains(":poison:")).toList();

        assertFalse(poison.isEmpty());
        assertTrue(poison.stream().allMatch(event -> event.impact().latest() <= 60));
        assertEquals(new TickWindow(55, 55), poison.getLast().impact());
    }

    @Test
    void stackedStatusAlsoStopsAtDecisionHorizon() {
        EngineLimits limits = new EngineLimits(128, 32, 80, 60);
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("potion_wither_duration_ticks", "40");
        properties.put("potion_wither_amplifier", "1");
        properties.put("potion_splash_radius", "4.0");
        properties.put("potion_duration_scale", "1.0");
        properties.put("potion_status_count", "2");
        properties.put("potion_status_0_kind", "wither");
        properties.put("potion_status_0_duration_ticks", "240");
        properties.put("potion_status_0_amplifier", "0");
        properties.put("potion_status_1_kind", "wither");
        properties.put("potion_status_1_duration_ticks", "40");
        properties.put("potion_status_1_amplifier", "1");

        List<ThreatEvent> tail = EnvironmentPredictorRegistry.defaults().predict(context(
            splash(properties),
            List.of(),
            limits
        )).stream().filter(event -> event.id().contains(":stacked_status:wither:")).toList();

        assertFalse(tail.isEmpty());
        assertTrue(tail.stream().allMatch(event -> event.impact().latest() <= 60));
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot entity,
        List<WorldSnapshot.BlockSnapshot> blocks,
        EngineLimits limits
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), blocks),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            limits
        );
    }

    private static WorldSnapshot.EntitySnapshot splash(Map<String, String> properties) {
        return potion("horizon:splash", "minecraft:splash_potion", properties);
    }

    private static WorldSnapshot.EntitySnapshot lingering(Map<String, String> properties) {
        return potion("horizon:lingering", "minecraft:lingering_potion", properties);
    }

    private static WorldSnapshot.EntitySnapshot potion(String id, String type, Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            type,
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            properties
        );
    }
}
