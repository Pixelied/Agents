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

class MeleePredictorTest {
    private final MeleePredictor predictor = new MeleePredictor();

    @Test
    void potentialAttackWithinReachIsNotClaimedExact() {
        ThreatEvent event = predictor.predict(context(attacker(Map.of(
            "melee_capable", "true",
            "attack_damage", "10",
            "attack_range", "3",
            "attack_strength", "1",
            "weapon_key", "minecraft:diamond_sword",
            "critical_possible", "false"
        )))).getFirst();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertEquals(new TickWindow(0, 2), event.impact());
    }

    @Test
    void maceSmashUsesDedicatedSourceTag() {
        ThreatEvent event = predictor.predict(context(attacker(Map.of(
            "melee_capable", "true",
            "attack_damage", "6",
            "attack_range", "3",
            "attack_strength", "1",
            "weapon_key", "minecraft:mace",
            "fall_distance", "4",
            "critical_possible", "false"
        )))).getFirst();

        assertTrue(event.damage().flags().contains(DamageFlag.IS_MACE_SMASH));
        assertEquals("minecraft:mace_smash", event.damage().sourceKey());
        assertEquals(20f, event.damage().rawDamage().max(), 0.0001f);
    }

    @Test
    void maceBonusMatchesVanillaBreakpoints() {
        assertEquals(0f, WeaponSnapshot.maceSmashBonus(1.5), 0.0001f);
        assertEquals(12f, WeaponSnapshot.maceSmashBonus(3), 0.0001f);
        assertEquals(22f, WeaponSnapshot.maceSmashBonus(8), 0.0001f);
        assertEquals(24f, WeaponSnapshot.maceSmashBonus(10), 0.0001f);
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot attacker) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot attacker(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "attacker:1",
            "minecraft:player",
            new Vec3Snapshot(2, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(2, 0, 0, 2.6, 1.8, 0.6),
            properties
        );
    }
}
