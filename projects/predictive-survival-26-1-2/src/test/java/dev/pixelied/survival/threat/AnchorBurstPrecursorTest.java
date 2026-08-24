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
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorBurstPrecursorTest {
    @Test
    void unchargedDangerousAnchorPlusVisibleGlowstoneHolderCreatesPrechargeBurstThreat() {
        WorldSnapshot.EntitySnapshot attacker = new WorldSnapshot.EntitySnapshot(
            "attacker:anchor",
            "minecraft:player",
            new Vec3Snapshot(4.0, 0.0, 0.0),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(3.7, 0.0, -0.3, 4.3, 1.8, 0.3),
            Map.of(
                "melee_capable", "true",
                "melee_model", "player",
                "weapon_key", "minecraft:glowstone",
                "offhand_item_key", "minecraft:air",
                "block_interaction_range", "4.5"
            )
        );
        WorldSnapshot.BlockSnapshot anchor = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(2.5, 0.5, 0.5),
            "minecraft:respawn_anchor",
            true,
            Map.of(
                "full_collision_cube", "true",
                "anchor_explodes", "true",
                "anchor_charge", "0",
                "pre_explosion_remove_group", "anchor:2,0,0"
            )
        );

        var event = new ExplosionPredictor().predict(context(attacker, anchor)).stream()
            .filter(candidate -> candidate.id().startsWith("burst:anchor-charge:"))
            .findFirst()
            .orElseThrow();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertEquals(0L, event.impact().earliest());
        assertTrue(event.damage().rawDamage().max() > 20f);
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot attacker,
        WorldSnapshot.BlockSnapshot anchor
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), List.of(anchor)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
