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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TntMinecartDestructiveIgnitionOpportunityTest {
    @Test
    void observedBurningNonArrowProjectileHitCreatesRandomShortFuseOpportunity() {
        WorldSnapshot.EntitySnapshot cart = new WorldSnapshot.EntitySnapshot(
            "cart",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(2.0, 0.0, 0.5),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(1.51, 0.0, 0.01, 2.49, 0.7, 0.99),
            Map.ofEntries(
                Map.entry("tnt_minecart", "true"),
                Map.entry("tnt_minecart_primed", "false"),
                Map.entry("horizontal_collision", "false"),
                Map.entry("fall_distance", "0.0"),
                Map.entry("tnt_explodes", "unknown"),
                Map.entry("server_hidden_explosion_power", "true"),
                Map.entry("source_key", "minecraft:explosion"),
                Map.entry("scales_with_difficulty", "true")
            )
        );
        WorldSnapshot.EntitySnapshot snowball = new WorldSnapshot.EntitySnapshot(
            "snowball",
            "minecraft:snowball",
            new Vec3Snapshot(3.0, 0.35, 0.5),
            new Vec3Snapshot(-1.0, 0.0, 0.0),
            new AabbSnapshot(2.95, 0.30, 0.45, 3.05, 0.40, 0.55),
            Map.of(
                "projectile", "true",
                "on_fire", "true"
            )
        );

        List<LethalOpportunity> opportunities = new TntMinecartOpportunityPredictor().predict(
            context(List.of(cart, snowball))
        );

        assertEquals(1, opportunities.size());
        LethalOpportunity opportunity = opportunities.getFirst();
        assertEquals("destructive_burning_projectile", opportunity.evidence().get("trigger"));
        assertEquals(new TickWindow(0, 39), opportunity.projectedThreat().impact());
        assertEquals("0", opportunity.evidence().get("random_fuse_min"));
        assertEquals("38", opportunity.evidence().get("random_fuse_max"));
        assertTrue(opportunity.projectedThreat().damage().rawDamage().max() > 0f);
    }

    private static PredictionContext context(List<WorldSnapshot.EntitySnapshot> entities) {
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
            new WorldSnapshot(entities, List.of()),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            SafetyMode.BALANCED
        );
    }
}
