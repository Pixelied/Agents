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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TntMinecartOpportunityPredictorTest {
    @Test
    void currentHorizontalCollisionAtThresholdCreatesImmediateOpportunity() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(2.0, 0.0, 0.5),
            new Vec3Snapshot(0.1, 0.0, 0.0),
            true,
            0.0,
            true
        );

        LethalOpportunity opportunity = only(predict(List.of(cart), List.of(), SafetyMode.BALANCED));

        assertEquals(OpportunityFamily.TNT_MINECART, opportunity.family());
        assertEquals(0, opportunity.actionDepth());
        assertEquals("horizontal_collision", opportunity.evidence().get("trigger"));
        assertTrue(opportunity.projectedThreat().damage().rawDamage().max() > 0f);
    }

    @Test
    void horizontalCollisionBelowVanillaSpeedThresholdDoesNotCreateOpportunity() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(2.0, 0.0, 0.5),
            new Vec3Snapshot(0.05, 0.0, 0.0),
            true,
            0.0,
            true
        );

        assertTrue(predict(List.of(cart), List.of(), SafetyMode.BALANCED).isEmpty());
    }

    @Test
    void nextTickCollisionWithExactBlockComponentCreatesForecastOpportunity() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(2.0, 0.0, 0.5),
            new Vec3Snapshot(0.8, 0.0, 0.0),
            false,
            0.0,
            true
        );
        WorldSnapshot.BlockSnapshot wall = fullBlock(3, 0, 0);

        LethalOpportunity opportunity = only(predict(List.of(cart), List.of(wall), SafetyMode.BALANCED));

        assertEquals("forecast_horizontal_collision", opportunity.evidence().get("trigger"));
        assertEquals(1, opportunity.projectedThreat().impact().earliest());
    }

    @Test
    void collisionTwoTicksAheadIsForecastBeforeOnePacketProtectionDeadline() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(1.4, 0.0, 0.5),
            new Vec3Snapshot(0.8, 0.0, 0.0),
            false,
            0.0,
            true
        );
        WorldSnapshot.BlockSnapshot wall = fullBlock(3, 0, 0);

        LethalOpportunity opportunity = only(predict(List.of(cart), List.of(wall), SafetyMode.BALANCED));

        assertEquals("forecast_horizontal_collision", opportunity.evidence().get("trigger"));
        assertEquals(2, opportunity.projectedThreat().impact().earliest());
    }

    @Test
    void nextTickPathThroughCompoundShapeGapDoesNotInventCollision() {
        WorldSnapshot.EntitySnapshot cart = new WorldSnapshot.EntitySnapshot(
            "cart",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(2.0, 0.5, 0.5),
            new Vec3Snapshot(0.8, 0.0, 0.0),
            new AabbSnapshot(1.6, 0.4, 0.1, 2.4, 0.6, 0.9),
            minecartProperties(false, 0.0, "true")
        );
        WorldSnapshot.BlockSnapshot split = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(3.5, 0.5, 0.5),
            "minecraft:test_split",
            true,
            List.of(
                new AabbSnapshot(3.0, 0.0, 0.0, 4.0, 0.25, 1.0),
                new AabbSnapshot(3.0, 0.75, 0.0, 4.0, 1.0, 1.0)
            ),
            Map.of("full_collision_cube", "false")
        );

        assertTrue(predict(List.of(cart), List.of(split), SafetyMode.BALANCED).isEmpty());
    }

    @Test
    void burningArrowImpactCreatesImmediateExplosionOpportunity() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(2.0, 0.0, 0.5),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            false,
            0.0,
            true
        );
        WorldSnapshot.EntitySnapshot arrow = new WorldSnapshot.EntitySnapshot(
            "arrow",
            "minecraft:arrow",
            new Vec3Snapshot(3.0, 0.35, 0.5),
            new Vec3Snapshot(-1.0, 0.0, 0.0),
            new AabbSnapshot(2.95, 0.30, 0.45, 3.05, 0.40, 0.55),
            Map.of("on_fire", "true")
        );

        LethalOpportunity opportunity = only(predict(List.of(cart, arrow), List.of(), SafetyMode.BALANCED));

        assertEquals("burning_arrow", opportunity.evidence().get("trigger"));
        assertEquals("1.0", opportunity.evidence().get("projectile_speed_sqr"));
    }

    @Test
    void fallDistanceAtLeastThreeWithNextTickGroundContactCreatesOpportunity() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(2.0, 1.4, 0.5),
            new Vec3Snapshot(0.0, -0.8, 0.0),
            false,
            3.0,
            true
        );
        WorldSnapshot.BlockSnapshot ground = fullBlock(2, 0, 0);

        LethalOpportunity opportunity = only(predict(List.of(cart), List.of(ground), SafetyMode.BALANCED));

        assertEquals("fall_impact", opportunity.evidence().get("trigger"));
        assertEquals(1, opportunity.projectedThreat().impact().earliest());
    }

    @Test
    void unknownTntExplodesGameRuleFailsClosedToPotentialExplosion() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(2.0, 0.0, 0.5),
            new Vec3Snapshot(0.2, 0.0, 0.0),
            true,
            0.0,
            "unknown"
        );

        LethalOpportunity opportunity = only(predict(List.of(cart), List.of(), SafetyMode.BALANCED));

        assertEquals("unknown", opportunity.evidence().get("tnt_explodes"));
    }

    @Test
    void disabledTntExplodesGameRuleSuppressesMinecartOpportunity() {
        WorldSnapshot.EntitySnapshot cart = minecart(
            new Vec3Snapshot(2.0, 0.0, 0.5),
            new Vec3Snapshot(0.2, 0.0, 0.0),
            true,
            0.0,
            false
        );

        assertTrue(predict(List.of(cart), List.of(), SafetyMode.SAFE).isEmpty());
    }

    private static List<LethalOpportunity> predict(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks,
        SafetyMode mode
    ) {
        return new TntMinecartOpportunityPredictor().predict(context(entities, blocks, mode));
    }

    private static LethalOpportunity only(List<LethalOpportunity> opportunities) {
        assertEquals(1, opportunities.size());
        return opportunities.getFirst();
    }

    private static WorldSnapshot.EntitySnapshot minecart(
        Vec3Snapshot position,
        Vec3Snapshot velocity,
        boolean horizontalCollision,
        double fallDistance,
        boolean tntExplodes
    ) {
        return minecart(position, velocity, horizontalCollision, fallDistance, Boolean.toString(tntExplodes));
    }

    private static WorldSnapshot.EntitySnapshot minecart(
        Vec3Snapshot position,
        Vec3Snapshot velocity,
        boolean horizontalCollision,
        double fallDistance,
        String tntExplodes
    ) {
        return new WorldSnapshot.EntitySnapshot(
            "cart",
            "minecraft:tnt_minecart",
            position,
            velocity,
            new AabbSnapshot(
                position.x() - 0.49, position.y(), position.z() - 0.49,
                position.x() + 0.49, position.y() + 0.7, position.z() + 0.49
            ),
            minecartProperties(horizontalCollision, fallDistance, tntExplodes)
        );
    }

    private static Map<String, String> minecartProperties(
        boolean horizontalCollision,
        double fallDistance,
        String tntExplodes
    ) {
        return Map.ofEntries(
            Map.entry("tnt_minecart", "true"),
            Map.entry("tnt_minecart_primed", "false"),
            Map.entry("horizontal_collision", Boolean.toString(horizontalCollision)),
            Map.entry("fall_distance", Double.toString(fallDistance)),
            Map.entry("tnt_explodes", tntExplodes),
            Map.entry("explosion_radius_default_min", "4.0"),
            Map.entry("explosion_radius_default_max", "11.5"),
            Map.entry("explosion_radius_hidden_min", "0.0"),
            Map.entry("explosion_radius_hidden_max", "1088.0"),
            Map.entry("server_hidden_explosion_power", "true"),
            Map.entry("source_key", "minecraft:explosion"),
            Map.entry("scales_with_difficulty", "true")
        );
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks,
        SafetyMode mode
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
            new WorldSnapshot(new ArrayList<>(entities), blocks),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            mode
        );
    }

    private static WorldSnapshot.BlockSnapshot fullBlock(int x, int y, int z) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(x + 0.5, y + 0.5, z + 0.5),
            "minecraft:stone",
            true,
            List.of(new AabbSnapshot(x, y, z, x + 1.0, y + 1.0, z + 1.0)),
            Map.of("full_collision_cube", "true")
        );
    }
}
