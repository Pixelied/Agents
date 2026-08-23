package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandReplacementSimulationTest {
    @Test
    void offhandDeathProtectionReplacementStopsOffhandShieldBlocking() {
        BlockingSnapshot activeShield = new BlockingSnapshot(
            true,
            1f,
            5,
            5,
            Optional.of(BlockingProfileSnapshot.fullBlock(336)),
            0
        );
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            activeShield,
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 64, 0, 0.6, 65.8, 0.6),
            new Vec3Snapshot(0, 64, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of("offhand", "minecraft:shield")
        );
        SurvivalAction.EquipDeathProtection action = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            1,
            true,
            true,
            1d,
            1,
            1
        );

        PlayerSnapshot after = action.apply(player);

        assertTrue(after.deathProtection().offHandAvailable());
        assertFalse(after.blocking().active(),
            "a contingency step cannot keep blocking with a shield after that same hand has been replaced");
    }
}
