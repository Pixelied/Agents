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
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactHazardPredictorTest {
    @Test
    void currentCactusContactPredictsImmediateRawOne() {
        ThreatEvent event = new ContactHazardPredictor().predict(context(Map.of("contact_cactus", "true"))).getFirst();
        assertEquals(new TickWindow(0, 1), event.impact());
        assertEquals(1f, event.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:cactus", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
    }

    @Test
    void movingBerryContactPredictsImmediateRawOne() {
        ThreatEvent event = new ContactHazardPredictor().predict(context(Map.of("contact_sweet_berry_bush", "true"))).getFirst();
        assertEquals(1f, event.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:sweet_berry_bush", event.damage().sourceKey());
    }

    @Test
    void litSoulCampfireUsesConfiguredRawTwoFireDamage() {
        ThreatEvent event = new ContactHazardPredictor().predict(context(Map.of("contact_campfire_damage", "2"))).getFirst();
        assertEquals(2f, event.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:campfire", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.IS_FIRE));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
    }

    @Test
    void magmaContactUsesHotFloorFireSource() {
        ThreatEvent event = new ContactHazardPredictor().predict(context(Map.of("contact_hot_floor", "true"))).getFirst();
        assertEquals(1f, event.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:hot_floor", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.IS_FIRE));
    }

    @Test
    void defaultEnvironmentRegistryIncludesCurrentContactHazards() {
        List<ThreatEvent> events = EnvironmentPredictorRegistry.defaults().predict(
            context(Map.of("contact_cactus", "true"))
        );
        assertTrue(events.stream().anyMatch(event -> "minecraft:cactus".equals(event.damage().sourceKey())));
    }

    @Test
    void noCurrentContactProducesNoThreat() {
        assertTrue(new ContactHazardPredictor().predict(context(Map.of())).isEmpty());
    }

    private static PredictionContext context(Map<String, String> state) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of(), state
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
