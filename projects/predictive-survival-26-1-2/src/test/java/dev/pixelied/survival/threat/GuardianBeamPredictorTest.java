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

class GuardianBeamPredictorTest {
    @Test
    void activeGuardianBeamPredictsMagicThenMeleeSequence() {
        List<ThreatEvent> events = new GuardianBeamPredictor().predict(context(
            DifficultySnapshot.NORMAL,
            "minecraft:guardian",
            Map.of(
                "guardian_beam_target_local", "true",
                "guardian_attack_ticks", "30",
                "guardian_attack_duration", "80",
                "direct_damage", "6.0"
            )
        ));

        assertEquals(2, events.size());
        ThreatEvent magic = events.stream().filter(event -> event.id().endsWith(":magic")).findFirst().orElseThrow();
        ThreatEvent melee = events.stream().filter(event -> event.id().endsWith(":melee")).findFirst().orElseThrow();
        assertEquals(new TickWindow(0, 50), magic.impact());
        assertEquals(magic.impact(), melee.impact());
        assertEquals(1f, magic.damage().rawDamage().min(), 0.0001f);
        assertEquals(1f, magic.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:indirect_magic", magic.damage().sourceKey());
        assertTrue(magic.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertTrue(magic.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
        assertEquals(6f, melee.damage().rawDamage().min(), 0.0001f);
        assertEquals("minecraft:mob_attack", melee.damage().sourceKey());
        assertTrue(melee.damage().scalesWithDifficulty());
    }

    @Test
    void hardElderGuardianBeamAddsBothVanillaMagicBonuses() {
        List<ThreatEvent> events = new GuardianBeamPredictor().predict(context(
            DifficultySnapshot.HARD,
            "minecraft:elder_guardian",
            Map.of(
                "guardian_beam_target_local", "true",
                "guardian_attack_ticks", "70",
                "guardian_attack_duration", "80",
                "direct_damage_min", "7.0",
                "direct_damage_max", "9.0"
            )
        ));

        ThreatEvent magic = events.stream().filter(event -> event.id().endsWith(":magic")).findFirst().orElseThrow();
        assertEquals(5f, magic.damage().rawDamage().min(), 0.0001f);
        assertEquals(5f, magic.damage().rawDamage().max(), 0.0001f);
        assertEquals(new TickWindow(0, 10), magic.impact());
        ThreatEvent melee = events.stream().filter(event -> event.id().endsWith(":melee")).findFirst().orElseThrow();
        assertEquals(7f, melee.damage().rawDamage().min(), 0.0001f);
        assertEquals(9f, melee.damage().rawDamage().max(), 0.0001f);
    }

    @Test
    void guardianBeamTargetingSomeoneElseProducesNoThreat() {
        assertTrue(new GuardianBeamPredictor().predict(context(
            DifficultySnapshot.NORMAL,
            "minecraft:guardian",
            Map.of(
                "guardian_beam_target_local", "false",
                "guardian_attack_ticks", "30",
                "guardian_attack_duration", "80",
                "direct_damage", "6.0"
            )
        )).isEmpty());
    }

    private static PredictionContext context(
        DifficultySnapshot difficulty,
        String type,
        Map<String, String> properties
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, difficulty,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        WorldSnapshot.EntitySnapshot guardian = new WorldSnapshot.EntitySnapshot(
            "guardian:1", type,
            new Vec3Snapshot(0, 1, 12), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(-0.5, 0.5, 11.5, 0.5, 1.5, 12.5), properties
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(guardian), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
