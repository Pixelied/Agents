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
    void failClosedRemotePlayerBoundsIncludeAFullStrengthPotentialHit() {
        List<ThreatEvent> events = predictor.predict(context(attacker(Map.ofEntries(
            Map.entry("melee_capable", "true"),
            Map.entry("melee_model", "player"),
            Map.entry("attack_damage_min", "0"),
            Map.entry("attack_damage_max", Float.toString(Float.MAX_VALUE)),
            Map.entry("attack_range", "3"),
            Map.entry("attack_strength_min", "0"),
            Map.entry("attack_strength_max", "1"),
            Map.entry("weapon_key", "minecraft:diamond_sword"),
            Map.entry("fall_distance_min", "0"),
            Map.entry("fall_distance_max", Float.toString(Float.MAX_VALUE)),
            Map.entry("critical_possible", "unknown"),
            Map.entry("armor_effectiveness_adjustment", "-0.30"),
            Map.entry("fire_aspect_level", "1")
        ))));

        ThreatEvent direct = events.stream().filter(e -> e.id().equals("melee:attacker:1")).findFirst().orElseThrow();
        ThreatEvent burn = events.stream().filter(e -> e.damage().sourceKey().equals("minecraft:on_fire")).findFirst().orElseThrow();
        assertEquals(0f, direct.damage().rawDamage().min(), 0.0001f);
        assertEquals(Float.MAX_VALUE, direct.damage().rawDamage().max());
        assertEquals(-0.30f, direct.damage().armorEffectivenessAdjustment(), 0.0001f);
        assertEquals(Confidence.POTENTIAL, direct.confidence());
        assertEquals(direct.id(), burn.requiresAcceptedEventId().orElseThrow());
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

    @Test
    void spearRelativeSpeedRaisesKineticDamage() {
        ThreatEvent slow = predictor.predict(context(spearAttacker(6.0, 0.0))).getFirst();
        ThreatEvent fast = predictor.predict(context(spearAttacker(12.0, 0.0))).getFirst();

        assertEquals("minecraft:spear", fast.damage().sourceKey());
        assertTrue(fast.damage().rawDamage().max() > slow.damage().rawDamage().max());
        assertEquals(15f, fast.damage().rawDamage().max(), 0.0001f);
    }

    @Test
    void spearBelowRelativeSpeedConditionProducesNoDamageThreat() {
        WorldSnapshot.EntitySnapshot attacker = attacker(Map.ofEntries(
            Map.entry("melee_capable", "true"),
            Map.entry("attack_range", "4.5"),
            Map.entry("weapon_key", "minecraft:netherite_spear"),
            Map.entry("spear_base_mob_damage", "1"),
            Map.entry("spear_damage_multiplier", "1.2"),
            Map.entry("spear_damage_max_use_ticks", "102"),
            Map.entry("spear_damage_min_speed", "0"),
            Map.entry("spear_damage_min_relative_speed", "4.6"),
            Map.entry("spear_ticks_used", "20"),
            Map.entry("spear_attacker_speed_projection", "4.5"),
            Map.entry("spear_target_speed_projection", "0")
        ));

        assertTrue(predictor.predict(context(attacker)).isEmpty());
    }

    @Test
    void spearOutsideDamageUseWindowProducesNoDamageThreat() {
        WorldSnapshot.EntitySnapshot attacker = attacker(Map.ofEntries(
            Map.entry("melee_capable", "true"),
            Map.entry("attack_range", "4.5"),
            Map.entry("weapon_key", "minecraft:netherite_spear"),
            Map.entry("spear_base_mob_damage", "1"),
            Map.entry("spear_damage_multiplier", "1.2"),
            Map.entry("spear_damage_max_use_ticks", "102"),
            Map.entry("spear_damage_min_speed", "0"),
            Map.entry("spear_damage_min_relative_speed", "4.6"),
            Map.entry("spear_ticks_used", "103"),
            Map.entry("spear_attacker_speed_projection", "12"),
            Map.entry("spear_target_speed_projection", "0")
        ));

        assertTrue(predictor.predict(context(attacker)).isEmpty());
    }


    @Test
    void mobRangeUsesSourceFaithfulAttackBoxInsteadOfLegacyInteractionRange() {
        WorldSnapshot.EntitySnapshot mob = new WorldSnapshot.EntitySnapshot(
            "mob:1", "minecraft:wither_skeleton", new Vec3Snapshot(2, 0, 0), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(2, 0, 0, 2.6, 1.8, 0.6),
            Map.ofEntries(
                Map.entry("melee_capable", "true"),
                Map.entry("melee_model", "mob"),
                Map.entry("attack_range", "100"),
                Map.entry("mob_attack_range_min", "0"),
                Map.entry("mob_attack_range_max", "0.5"),
                Map.entry("direct_damage", "10"),
                Map.entry("weapon_key", "minecraft:stone_sword")
            )
        );

        assertTrue(predictor.predict(context(mob)).isEmpty());
    }

    @Test
    void mobDirectDamageUsesReconstructedServerRangeInsteadOfUnsyncedAttackDamage() {
        WorldSnapshot.EntitySnapshot mob = new WorldSnapshot.EntitySnapshot(
            "mob:2", "minecraft:iron_golem", new Vec3Snapshot(0.7, 0, 0), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(0.7, 0, 0, 1.3, 2.7, 0.6),
            Map.ofEntries(
                Map.entry("melee_capable", "true"),
                Map.entry("melee_model", "mob"),
                Map.entry("mob_attack_range_min", "0"),
                Map.entry("mob_attack_range_max", "1.5"),
                Map.entry("attack_damage", "100"),
                Map.entry("direct_damage_min", "7.5"),
                Map.entry("direct_damage_max", "21.5"),
                Map.entry("weapon_key", "minecraft:mace"),
                Map.entry("fall_distance", "10")
            )
        );

        ThreatEvent event = predictor.predict(context(mob)).getFirst();
        assertEquals(7.5f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(21.5f, event.damage().rawDamage().max(), 0.0001f);
    }


    @Test
    void witherSkeletonCreatesCausalWitherFollowup() {
        WorldSnapshot.EntitySnapshot mob = new WorldSnapshot.EntitySnapshot(
            "mob:wither", "minecraft:wither_skeleton", new Vec3Snapshot(0.7, 0, 0), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(0.7, 0, 0, 1.3, 2.4, 0.6),
            Map.ofEntries(
                Map.entry("melee_capable", "true"), Map.entry("melee_model", "mob"),
                Map.entry("mob_attack_range_min", "0"), Map.entry("mob_attack_range_max", "1.5"),
                Map.entry("direct_damage", "5"), Map.entry("wither_followup_ticks", "200")
            )
        );

        List<ThreatEvent> events = predictor.predict(context(mob));
        ThreatEvent direct = events.stream().filter(e -> e.id().equals("melee:mob:wither")).findFirst().orElseThrow();
        ThreatEvent wither = events.stream().filter(e -> e.damage().sourceKey().equals("minecraft:wither")).findFirst().orElseThrow();
        assertEquals(direct.id(), wither.requiresAcceptedEventId().orElseThrow());
        assertEquals(1f, wither.damage().rawDamage().max(), 0.0001f);
    }

    @Test
    void fireAspectCreatesCausalOnFireFollowup() {
        WorldSnapshot.EntitySnapshot mob = new WorldSnapshot.EntitySnapshot(
            "mob:fire", "minecraft:zombie", new Vec3Snapshot(0.7, 0, 0), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(0.7, 0, 0, 1.3, 1.95, 0.6),
            Map.ofEntries(
                Map.entry("melee_capable", "true"), Map.entry("melee_model", "mob"),
                Map.entry("mob_attack_range_min", "0"), Map.entry("mob_attack_range_max", "1.5"),
                Map.entry("direct_damage", "5"), Map.entry("fire_aspect_level", "1")
            )
        );

        List<ThreatEvent> events = predictor.predict(context(mob));
        ThreatEvent direct = events.stream().filter(e -> e.id().equals("melee:mob:fire")).findFirst().orElseThrow();
        ThreatEvent burn = events.stream().filter(e -> e.damage().sourceKey().equals("minecraft:on_fire")).findFirst().orElseThrow();
        assertEquals(direct.id(), burn.requiresAcceptedEventId().orElseThrow());
    }

    private static WorldSnapshot.EntitySnapshot spearAttacker(double attackerProjection, double targetProjection) {
        return attacker(Map.ofEntries(
            Map.entry("melee_capable", "true"),
            Map.entry("attack_range", "4.5"),
            Map.entry("weapon_key", "minecraft:netherite_spear"),
            Map.entry("spear_base_mob_damage", "1"),
            Map.entry("spear_damage_multiplier", "1.2"),
            Map.entry("spear_damage_max_use_ticks", "102"),
            Map.entry("spear_damage_min_speed", "0"),
            Map.entry("spear_damage_min_relative_speed", "4.6"),
            Map.entry("spear_ticks_used", "20"),
            Map.entry("spear_attacker_speed_projection", Double.toString(attackerProjection)),
            Map.entry("spear_target_speed_projection", Double.toString(targetProjection))
        ));
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
