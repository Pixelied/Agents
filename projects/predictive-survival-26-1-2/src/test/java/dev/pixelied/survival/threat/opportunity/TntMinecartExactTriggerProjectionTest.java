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

import static org.junit.jupiter.api.Assertions.assertTrue;

class TntMinecartExactTriggerProjectionTest {
    @Test
    void exactForecastCollisionDoesNotApplyCartVelocityTwice() {
        WorldSnapshot.EntitySnapshot cart = new WorldSnapshot.EntitySnapshot(
            "cart",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(10.0, 0.0, 0.5),
            new Vec3Snapshot(-2.5, 0.0, 0.0),
            new AabbSnapshot(9.51, 0.0, 0.01, 10.49, 0.7, 0.99),
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
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(7.5, 0.5, 0.5),
            "minecraft:stone",
            true,
            List.of(new AabbSnapshot(7.0, 0.0, 0.0, 8.0, 1.0, 1.0)),
            Map.of("full_collision_cube", "true")
        );

        List<LethalOpportunity> opportunities = new TntMinecartOpportunityPredictor().predict(
            context(List.of(cart), List.of(wall))
        );

        assertTrue(opportunities.isEmpty(),
            "the exact tick-1 explosion is inside the shielding wall; projecting velocity again would invent a lethal center on the player side");
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
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
            new WorldSnapshot(entities, blocks),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 1)),
            EngineLimits.defaults(),
            SafetyMode.BALANCED
        );
    }
}
