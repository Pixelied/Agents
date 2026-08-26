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

class TntMinecartCollisionContactTest {
    @Test
    void fastForecastCollisionClampsExplosionToContactBeforeWall() {
        WorldSnapshot.EntitySnapshot cart = new WorldSnapshot.EntitySnapshot(
            "cart",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(0.5, 0.0, 2.0),
            new Vec3Snapshot(0.0, 0.0, 1.2),
            new AabbSnapshot(0.01, 0.0, 1.51, 0.99, 0.7, 2.49),
            Map.ofEntries(
                Map.entry("tnt_minecart", "true"),
                Map.entry("tnt_minecart_primed", "false"),
                Map.entry("horizontal_collision", "false"),
                Map.entry("fall_distance", "0.0"),
                Map.entry("tnt_explodes", "unknown"),
                Map.entry("explosion_radius_default_min", "4.0"),
                Map.entry("explosion_radius_default_max", "11.5"),
                Map.entry("explosion_radius_hidden_min", "0.0"),
                Map.entry("explosion_radius_hidden_max", "1088.0"),
                Map.entry("server_hidden_explosion_power", "true"),
                Map.entry("source_key", "minecraft:explosion"),
                Map.entry("scales_with_difficulty", "true")
            )
        );
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(0.5, 0.5, 3.5),
            "minecraft:obsidian",
            true,
            List.of(new AabbSnapshot(0.0, 0.0, 3.0, 1.0, 1.0, 4.0)),
            Map.of("full_collision_cube", "true")
        );
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
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(cart), List.of(wall)),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            SafetyMode.BALANCED
        );

        List<LethalOpportunity> opportunities = new TntMinecartOpportunityPredictor().predict(context);

        assertEquals(1, opportunities.size());
        LethalOpportunity opportunity = opportunities.getFirst();
        assertEquals("forecast_horizontal_collision", opportunity.evidence().get("trigger"));
        Vec3Snapshot explosionCenter = opportunity.projectedThreat().sourcePosition().orElseThrow();
        assertTrue(explosionCenter.z() < 3.0, "collision explosion must remain on the near side of the wall: " + explosionCenter);
        assertTrue(explosionCenter.z() >= 2.49, "collision explosion should advance to contact: " + explosionCenter);
    }
}
