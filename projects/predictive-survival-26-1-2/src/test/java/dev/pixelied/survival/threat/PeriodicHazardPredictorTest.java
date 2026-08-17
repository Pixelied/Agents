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
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import dev.pixelied.survival.timeline.TimelineResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodicHazardPredictorTest {
    @Test
    void poisonStopsAtOneHealthAcrossMultipleScheduledTicks() {
        PlayerSnapshot player = player(
            4f,
            DifficultySnapshot.NORMAL,
            Map.of(),
            effects(new EffectInstanceSnapshot("minecraft:poison", 100, 0))
        );

        List<ThreatEvent> events = EnvironmentPredictorRegistry.defaults().predict(playerContext(player));
        TimelineResult result = new ThreatTimelineSimulator().simulate(player, new ThreatTimeline(events));

        assertTrue(result.survived());
        assertEquals(1f, result.finalHealth(), 0.0001f);
    }

    @Test
    void witherCanRemainLethal() {
        PlayerSnapshot player = player(
            3f,
            DifficultySnapshot.NORMAL,
            Map.of(),
            effects(new EffectInstanceSnapshot("minecraft:wither", 120, 0))
        );

        TimelineResult result = new ThreatTimelineSimulator().simulate(
            player,
            new ThreatTimeline(EnvironmentPredictorRegistry.defaults().predict(playerContext(player)))
        );

        assertFalse(result.survived());
    }

    @Test
    void onFireUsesRemainingFireTickPhase() {
        PlayerSnapshot player = player(
            20f,
            DifficultySnapshot.NORMAL,
            Map.of("remaining_fire_ticks", "22", "in_lava", "false", "fire_immune", "false"),
            StatusEffectsSnapshot.none()
        );

        ThreatEvent fire = findSource(EnvironmentPredictorRegistry.defaults().predict(playerContext(player)), "minecraft:on_fire");
        assertEquals(new TickWindow(3, 3), fire.impact());
    }

    @Test
    void drowningResetsAirAndRepeatsTwentyTicksLater() {
        PlayerSnapshot player = player(
            20f,
            DifficultySnapshot.NORMAL,
            Map.of(
                "eye_in_water", "true",
                "eye_in_bubble_column", "false",
                "can_breathe_underwater", "false",
                "oxygen_bonus", "0",
                "air_supply", "-19"
            ),
            StatusEffectsSnapshot.none()
        );

        List<ThreatEvent> drowning = EnvironmentPredictorRegistry.defaults().predict(playerContext(player)).stream()
            .filter(event -> event.damage().sourceKey().equals("minecraft:drown"))
            .toList();

        assertTrue(drowning.size() >= 2);
        assertEquals(new TickWindow(1, 1), drowning.get(0).impact());
        assertEquals(new TickWindow(21, 21), drowning.get(1).impact());
    }

    @Test
    void fullyFrozenDamageUsesGlobalFortyTickPhase() {
        PlayerSnapshot player = player(
            20f,
            DifficultySnapshot.NORMAL,
            Map.of("tick_count", "39", "fully_frozen", "true", "can_freeze", "true"),
            StatusEffectsSnapshot.none()
        );

        ThreatEvent freeze = findSource(EnvironmentPredictorRegistry.defaults().predict(playerContext(player)), "minecraft:freeze");
        assertEquals(new TickWindow(1, 1), freeze.impact());
    }

    @Test
    void starvationHealthFloorDependsOnDifficulty() {
        Map<String, String> starvation = Map.of("food_level", "0", "food_tick_timer", "79");
        PlayerSnapshot normal = player(1f, DifficultySnapshot.NORMAL, starvation, StatusEffectsSnapshot.none());
        PlayerSnapshot hard = player(1f, DifficultySnapshot.HARD, starvation, StatusEffectsSnapshot.none());

        assertFalse(hasSource(EnvironmentPredictorRegistry.defaults().predict(playerContext(normal)), "minecraft:starve"));
        assertEquals(
            new TickWindow(1, 1),
            findSource(EnvironmentPredictorRegistry.defaults().predict(playerContext(hard)), "minecraft:starve").impact()
        );
    }

    @Test
    void borderAndSuffocationRemainDiscretePerTickEvents() {
        PlayerSnapshot player = player(
            20f,
            DifficultySnapshot.NORMAL,
            Map.of(
                "in_wall", "true",
                "border_distance_plus_safe_zone", "-6",
                "border_damage_per_block", "0.5"
            ),
            StatusEffectsSnapshot.none()
        );

        List<ThreatEvent> events = EnvironmentPredictorRegistry.defaults().predict(playerContext(player));
        List<ThreatEvent> border = events.stream().filter(e -> e.damage().sourceKey().equals("minecraft:outside_border")).toList();
        List<ThreatEvent> wall = events.stream().filter(e -> e.damage().sourceKey().equals("minecraft:in_wall")).toList();

        assertTrue(border.size() >= 2);
        assertEquals(3f, border.getFirst().damage().rawDamage().max(), 0.0001f);
        assertEquals(new TickWindow(1, 1), border.get(0).impact());
        assertEquals(new TickWindow(2, 2), border.get(1).impact());
        assertEquals(new TickWindow(1, 1), wall.getFirst().impact());
    }

    @Test
    void lavaContactIsImmediateFourDamageFireThreat() {
        PlayerSnapshot player = player(
            20f,
            DifficultySnapshot.NORMAL,
            Map.of("in_lava", "true", "fire_immune", "false"),
            StatusEffectsSnapshot.none()
        );

        ThreatEvent lava = findSource(EnvironmentPredictorRegistry.defaults().predict(playerContext(player)), "minecraft:lava");
        assertEquals(4f, lava.damage().rawDamage().max(), 0.0001f);
        assertEquals(new TickWindow(1, 1), lava.impact());
    }

    private static PredictionContext playerContext(PlayerSnapshot player) {
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static PlayerSnapshot player(
        float health,
        DifficultySnapshot difficulty,
        Map<String, String> state,
        StatusEffectsSnapshot effects
    ) {
        return new PlayerSnapshot(
            health,
            0f,
            false,
            false,
            false,
            difficulty,
            MitigationSnapshot.none(),
            effects,
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            state
        );
    }

    private static StatusEffectsSnapshot effects(EffectInstanceSnapshot... instances) {
        Map<String, EffectInstanceSnapshot> map = new LinkedHashMap<>();
        for (EffectInstanceSnapshot instance : instances) map.put(instance.effectKey(), instance);
        return new StatusEffectsSnapshot(false, -1, map);
    }

    private static boolean hasSource(List<ThreatEvent> events, String source) {
        return events.stream().anyMatch(event -> event.damage().sourceKey().equals(source));
    }

    private static ThreatEvent findSource(List<ThreatEvent> events, String source) {
        return events.stream()
            .filter(event -> event.damage().sourceKey().equals(source))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing threat source " + source));
    }
}
