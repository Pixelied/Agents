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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectSplashStatusDurationScalePredictorTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void directSplashPoisonUsesPotionDurationScaleBeforeScheduling() {
        List<ThreatEvent> poison = predictor.predict(context(splash(Map.of(
            "potion_poison_duration_ticks", "100",
            "potion_poison_amplifier", "0",
            "potion_duration_scale", "0.5"
        )))).stream().filter(event -> event.id().contains(":poison:")).toList();

        assertEquals(
            List.of(new TickWindow(5, 5), new TickWindow(30, 30)),
            poison.stream().map(ThreatEvent::impact).toList()
        );
    }

    @Test
    void directSplashWitherDropsEffectAtVanillaTwentyTickCutoff() {
        List<ThreatEvent> wither = predictor.predict(context(splash(Map.of(
            "potion_wither_duration_ticks", "40",
            "potion_wither_amplifier", "0",
            "potion_duration_scale", "0.5"
        )))).stream().filter(event -> event.id().contains(":wither:")).toList();

        assertTrue(wither.isEmpty(), "vanilla discards non-instant splash effects whose scaled duration is <= 20 ticks");
    }

    @Test
    void scaledStrongCutoffDoesNotHideLongWeakWitherCadence() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("potion_wither_duration_ticks", "40");
        properties.put("potion_wither_amplifier", "1");
        properties.put("potion_duration_scale", "0.5");
        properties.put("potion_status_count", "2");
        properties.put("potion_status_0_kind", "wither");
        properties.put("potion_status_0_duration_ticks", "240");
        properties.put("potion_status_0_amplifier", "0");
        properties.put("potion_status_1_kind", "wither");
        properties.put("potion_status_1_duration_ticks", "40");
        properties.put("potion_status_1_amplifier", "1");

        PredictionContext context = context(splash(properties));
        List<ThreatEvent> wither = new ArrayList<>();
        wither.addAll(predictor.predict(context).stream()
            .filter(event -> event.id().contains(":wither:"))
            .toList());
        wither.addAll(new StackedPotionStatusPredictor().predict(context).stream()
            .filter(event -> event.id().contains(":wither:"))
            .toList());
        wither.sort(Comparator.comparingLong(event -> event.impact().earliest()));

        assertEquals(
            List.of(new TickWindow(5, 5), new TickWindow(45, 45), new TickWindow(85, 85)),
            wither.stream().map(ThreatEvent::impact).toList(),
            "the strong 40-tick source scales to the discarded 20-tick cutoff, leaving the weak 240-tick source scaled to Wither I 120"
        );
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot potion) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(potion), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot splash(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "direct:scaled",
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            properties
        );
    }
}
