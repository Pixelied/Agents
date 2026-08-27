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

class ProjectileReleaseOpportunityPredictorTest {
    @Test
    void loadedCrossbowArrowCreatesImmediateLethalReleaseOpportunity() {
        LethalOpportunity opportunity = only(predict(attacker(Map.of(
            "main_hand_item_key", "minecraft:crossbow",
            "main_hand_crossbow_projectile_kind", "arrow"
        )), 4f));

        assertEquals(OpportunityFamily.PROJECTILE, opportunity.family());
        assertEquals("crossbow_arrow", opportunity.evidence().get("release_family"));
        assertEquals("main_hand", opportunity.evidence().get("hand"));
        assertEquals(0, opportunity.projectedThreat().impact().earliest());
        assertEquals(7f, opportunity.projectedThreat().damage().rawDamage().max());
    }

    @Test
    void loadedCrossbowFireworkUsesVisibleExplosionCount() {
        LethalOpportunity opportunity = only(predict(attacker(Map.of(
            "main_hand_item_key", "minecraft:crossbow",
            "main_hand_crossbow_projectile_kind", "firework",
            "main_hand_crossbow_firework_explosions", "1"
        )), 4f));

        assertEquals("crossbow_firework", opportunity.evidence().get("release_family"));
        assertEquals(7f, opportunity.projectedThreat().damage().rawDamage().max());
    }

    @Test
    void windChargeCreatesImmediateOpportunityOnlyWhenItsOneDamageCanKill() {
        LethalOpportunity opportunity = only(predict(attacker(Map.of(
            "offhand_item_key", "minecraft:wind_charge"
        )), 0.5f));
        assertEquals("wind_charge", opportunity.evidence().get("release_family"));
        assertEquals("off_hand", opportunity.evidence().get("hand"));
        assertEquals(1f, opportunity.projectedThreat().damage().rawDamage().max());

        assertTrue(predict(attacker(Map.of("offhand_item_key", "minecraft:wind_charge")), 20f).isEmpty());
    }

    @Test
    void splashHarmingUsesVisiblePotionPayloadAndBypassesShieldArmorModel() {
        LethalOpportunity opportunity = only(predict(attacker(Map.of(
            "main_hand_item_key", "minecraft:splash_potion",
            "main_hand_potion_instant_damage", "12.0"
        )), 8f));

        assertEquals("splash_harming", opportunity.evidence().get("release_family"));
        assertEquals(12f, opportunity.projectedThreat().damage().rawDamage().max());
        assertTrue(opportunity.projectedThreat().damage().flags().stream().anyMatch(flag -> flag.name().equals("BYPASSES_ARMOR")));
        assertTrue(opportunity.projectedThreat().damage().flags().stream().anyMatch(flag -> flag.name().equals("BYPASSES_SHIELD")));
    }

    @Test
    void synchronizedBowUseCreatesBoundedReleaseOpportunityAfterAuthorityProbeLostItsGuarantee() {
        LethalOpportunity opportunity = only(predict(attacker(Map.of(
            "main_hand_item_key", "minecraft:bow",
            "using_item", "true",
            "used_hand", "main_hand",
            "client_observed_use_ticks", "3"
        )), 1f));

        assertEquals("bow_arrow", opportunity.evidence().get("release_family"));
        assertEquals("main_hand", opportunity.evidence().get("hand"));
        assertEquals("3", opportunity.evidence().get("client_observed_use_ticks"));
        assertTrue(opportunity.projectedThreat().damage().rawDamage().max() >= 1f);
    }

    @Test
    void heldBowWithoutSynchronizedUseDoesNotInventReleaseOpportunity() {
        assertTrue(predict(attacker(Map.of(
            "main_hand_item_key", "minecraft:bow",
            "using_item", "false",
            "used_hand", "none",
            "client_observed_use_ticks", "0"
        )), 1f).isEmpty());
    }

    @Test
    void bowUseOnTheOtherHandDoesNotInventMainHandRelease() {
        assertTrue(predict(attacker(Map.of(
            "main_hand_item_key", "minecraft:bow",
            "using_item", "true",
            "used_hand", "off_hand",
            "client_observed_use_ticks", "20"
        )), 1f).isEmpty());
    }

    @Test
    void blockedLineOfSightDoesNotInventReleaseOpportunity() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("main_hand_item_key", "minecraft:crossbow");
        properties.put("main_hand_crossbow_projectile_kind", "arrow");
        properties.put("line_of_sight", "false");
        assertTrue(predict(attacker(properties), 1f).isEmpty());
    }

    private static List<LethalOpportunity> predict(WorldSnapshot.EntitySnapshot attacker, float health) {
        try {
            Class<?> type = Class.forName(
                "dev.pixelied.survival.threat.opportunity.ProjectileReleaseOpportunityPredictor"
            );
            Object instance = type.getDeclaredConstructor().newInstance();
            return ((LethalOpportunityPredictor) instance).predict(context(attacker, health));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("projectile release opportunity predictor is not implemented", exception);
        }
    }

    private static LethalOpportunity only(List<LethalOpportunity> opportunities) {
        assertEquals(1, opportunities.size());
        return opportunities.getFirst();
    }

    private static WorldSnapshot.EntitySnapshot attacker(Map<String, String> overrides) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("main_hand_item_key", "minecraft:air");
        properties.put("offhand_item_key", "minecraft:air");
        properties.put("line_of_sight", "true");
        properties.put("eye_position_x", "0.9");
        properties.put("eye_position_y", "1.62");
        properties.put("eye_position_z", "0.3");
        properties.putAll(overrides);
        return new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            new Vec3Snapshot(0.9, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(0.6, 0.0, 0.0, 1.2, 1.8, 0.6),
            properties
        );
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot attacker, float health) {
        PlayerSnapshot player = new PlayerSnapshot(
            health,
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
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            SafetyMode.BALANCED
        );
    }
}
