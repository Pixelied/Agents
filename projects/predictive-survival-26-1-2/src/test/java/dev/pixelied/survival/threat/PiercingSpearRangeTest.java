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
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.MeleeApproachOpportunityPredictor;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the distinct 26.1.2 ServerboundPlayerActionPacket.STAB reach path. */
class PiercingSpearRangeTest {
    @Test
    void piercingSpearOutsideWeaponRayIsNotAlreadyAnActualMeleeThreat() {
        PredictionContext context = context(piercingSpearAttacker());

        assertTrue(new MeleePredictor().predict(context).isEmpty());
    }

    @Test
    void piercingSpearApproachUsesWeaponRayRangeInsteadOfAttackPacketBuffer() {
        PredictionContext context = context(piercingSpearAttacker());

        List<LethalOpportunity> opportunities = new MeleeApproachOpportunityPredictor().predict(context);

        assertEquals(1, opportunities.size());
        LethalOpportunity opportunity = opportunities.getFirst();
        assertEquals(new TickWindow(1, 1), opportunity.projectedThreat().impact());
        assertEquals("minecraft:spear", opportunity.projectedThreat().damage().sourceKey());
        assertEquals("piercing_weapon", opportunity.evidence().get("attack_profile"));
    }

    @Test
    void piercingSpearKnownMovementExtendsOnlyAlongTheChosenStabRay() {
        Vec3Snapshot eye = new Vec3Snapshot(0.0, 1.62, 0.0);
        AabbSnapshot target = new AabbSnapshot(-0.3, 0.0, 5.0, 0.3, 1.8, 5.6);
        ServerPlayerAttackRange.AttackProfile profile =
            new ServerPlayerAttackRange.AttackProfile(2.0, 4.5, 0.125, "piercing_weapon");

        assertFalse(ServerPlayerAttackRange.isWithin(
            eye,
            target,
            profile,
            new Vec3Snapshot(1.0, 0.0, 0.0)
        ));
        assertTrue(ServerPlayerAttackRange.isWithin(
            eye,
            target,
            profile,
            new Vec3Snapshot(0.0, 0.0, 0.5)
        ));
    }

    private static WorldSnapshot.EntitySnapshot piercingSpearAttacker() {
        Vec3Snapshot position = new Vec3Snapshot(5.6, 0.0, 0.3);
        return new WorldSnapshot.EntitySnapshot(
            "spear-attacker",
            "minecraft:player",
            position,
            new Vec3Snapshot(-0.25, 0.0, 0.0),
            new AabbSnapshot(5.3, 0.0, 0.0, 5.9, 1.8, 0.6),
            Map.ofEntries(
                Map.entry("melee_capable", "true"),
                Map.entry("melee_model", "player"),
                Map.entry("weapon_key", "minecraft:netherite_spear"),
                Map.entry("piercing_weapon", "true"),
                Map.entry("attack_damage_min", "0"),
                Map.entry("attack_damage_max", Float.toString(Float.MAX_VALUE)),
                Map.entry("attack_strength_min", "0"),
                Map.entry("attack_strength_max", "1"),
                Map.entry("attack_range", "4.5"),
                Map.entry("main_hand_attack_min_range", "2.0"),
                Map.entry("main_hand_attack_max_range", "4.5"),
                Map.entry("main_hand_attack_hitbox_margin", "0.125"),
                Map.entry("eye_position_x", "5.6"),
                Map.entry("eye_position_y", "1.62"),
                Map.entry("eye_position_z", "0.3"),
                Map.entry("critical_possible", "false"),
                Map.entry("line_of_sight", "true"),
                Map.entry("source_key", "minecraft:spear")
            )
        );
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot attacker) {
        PlayerSnapshot player = new PlayerSnapshot(
            4f,
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
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), List.of()),
            new TimingSnapshot(0L, 100d, 10d, new TickWindow(1L, 2L)),
            EngineLimits.defaults()
        );
    }
}
