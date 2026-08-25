package dev.pixelied.survival.threat.opportunity;

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
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleeApproachOpportunityPredictorTest {
    @Test
    void lethalMaceApproachAppearsBeforeCurrentReach() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(8.0, 0.0, 0.3),
            new Vec3Snapshot(-1.5, 0.0, 0.0),
            Map.ofEntries(
                Map.entry("weapon_key", "minecraft:mace"),
                Map.entry("attack_damage", "6"),
                Map.entry("attack_strength", "1"),
                Map.entry("fall_distance", "20"),
                Map.entry("critical_possible", "false"),
                Map.entry("attack_range", "3"),
                Map.entry("main_hand_attack_min_range", "0"),
                Map.entry("main_hand_attack_max_range", "3"),
                Map.entry("main_hand_attack_hitbox_margin", "0")
            )
        );

        LethalOpportunity opportunity = only(predict(attacker, EngineLimits.defaults()));

        assertEquals(OpportunityFamily.MELEE, opportunity.family());
        assertEquals(new TickWindow(1, 1), opportunity.projectedThreat().impact());
        assertEquals("minecraft:mace_smash", opportunity.projectedThreat().damage().sourceKey());
        assertTrue(opportunity.projectedThreat().damage().flags().contains(DamageFlag.IS_MACE_SMASH));
        assertEquals("1", opportunity.evidence().get("entry_tick"));
    }

    @Test
    void approachingKineticSpearReusesExistingSpearDamageRange() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(10.0, 0.0, 0.3),
            new Vec3Snapshot(-2.0, 0.0, 0.0),
            Map.ofEntries(
                Map.entry("weapon_key", "minecraft:netherite_spear"),
                Map.entry("attack_range", "4.5"),
                Map.entry("main_hand_attack_min_range", "0"),
                Map.entry("main_hand_attack_max_range", "4.5"),
                Map.entry("main_hand_attack_hitbox_margin", "0"),
                Map.entry("spear_base_mob_damage", "1"),
                Map.entry("spear_damage_multiplier", "1.2"),
                Map.entry("spear_damage_max_use_ticks", "102"),
                Map.entry("spear_damage_min_speed", "0"),
                Map.entry("spear_damage_min_relative_speed", "4.6"),
                Map.entry("spear_ticks_used", "20"),
                Map.entry("spear_attacker_speed_projection", "12"),
                Map.entry("spear_target_speed_projection", "0")
            )
        );

        LethalOpportunity opportunity = only(predict(attacker, EngineLimits.defaults()));

        assertEquals(new TickWindow(1, 1), opportunity.projectedThreat().impact());
        assertEquals("minecraft:spear", opportunity.projectedThreat().damage().sourceKey());
        assertEquals(15f, opportunity.projectedThreat().damage().rawDamage().max(), 0.0001f);
    }

    @Test
    void movingAwayDoesNotCreateApproachOpportunity() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(8.0, 0.0, 0.3),
            new Vec3Snapshot(1.5, 0.0, 0.0),
            Map.of("attack_damage", "40")
        );

        assertTrue(predict(attacker, EngineLimits.defaults()).isEmpty());
    }

    @Test
    void explicitLineOfSightFalseSuppressesApproachOpportunity() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(8.0, 0.0, 0.3),
            new Vec3Snapshot(-1.5, 0.0, 0.0),
            Map.ofEntries(
                Map.entry("attack_damage", "40"),
                Map.entry("line_of_sight", "false")
            )
        );

        assertTrue(predict(attacker, EngineLimits.defaults()).isEmpty());
    }

    @Test
    void entryAfterPredictionHorizonDoesNotCreateOpportunity() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(20.0, 0.0, 0.3),
            new Vec3Snapshot(-1.0, 0.0, 0.0),
            Map.of("attack_damage", "40")
        );
        EngineLimits shortHorizon = new EngineLimits(128, 32, 3, 128, 128);

        assertTrue(predict(attacker, shortHorizon).isEmpty());
    }

    @Test
    void nonLethalPostMitigationMeleeDoesNotCreateDeathProtectionOpportunity() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(8.0, 0.0, 0.3),
            new Vec3Snapshot(-1.5, 0.0, 0.0),
            Map.ofEntries(
                Map.entry("attack_damage", "1"),
                Map.entry("attack_strength", "1"),
                Map.entry("critical_possible", "false")
            )
        );

        assertTrue(predict(attacker, EngineLimits.defaults()).isEmpty());
    }

    @Test
    void alreadyWithinServerAttackRangeStaysOnActualThreatPath() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(5.0, 0.0, 0.3),
            new Vec3Snapshot(-1.0, 0.0, 0.0),
            Map.of("attack_damage", "40")
        );

        assertTrue(predict(attacker, EngineLimits.defaults()).isEmpty());
    }

    private static List<LethalOpportunity> predict(
        WorldSnapshot.EntitySnapshot attacker,
        EngineLimits limits
    ) {
        return new MeleeApproachOpportunityPredictor().predict(context(attacker, limits));
    }

    private static LethalOpportunity only(List<LethalOpportunity> opportunities) {
        assertEquals(1, opportunities.size());
        return opportunities.getFirst();
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot attacker,
        EngineLimits limits
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            4f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), List.of()),
            new TimingSnapshot(0, 100d, 10d, new TickWindow(1, 2)),
            limits,
            SafetyMode.BALANCED
        );
    }

    private static WorldSnapshot.EntitySnapshot attacker(
        Vec3Snapshot position,
        Vec3Snapshot velocity,
        Map<String, String> overrides
    ) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("melee_capable", "true");
        properties.put("melee_model", "player");
        properties.put("attack_damage", "40");
        properties.put("attack_strength", "1");
        properties.put("weapon_key", "minecraft:diamond_sword");
        properties.put("critical_possible", "false");
        properties.put("line_of_sight", "true");
        properties.put("attack_range", "3");
        properties.put("main_hand_attack_min_range", "0");
        properties.put("main_hand_attack_max_range", "3");
        properties.put("main_hand_attack_hitbox_margin", "0");
        properties.put("eye_position_x", Double.toString(position.x()));
        properties.put("eye_position_y", "1.62");
        properties.put("eye_position_z", Double.toString(position.z()));
        properties.putAll(overrides);

        return new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            position,
            velocity,
            new AabbSnapshot(
                position.x() - 0.3, position.y(), position.z() - 0.3,
                position.x() + 0.3, position.y() + 1.8, position.z() + 0.3
            ),
            Map.copyOf(properties)
        );
    }
}
