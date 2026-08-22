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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaiseShieldBlockingProfileTest {
    @Test
    void raisedShieldPreservesCapturedDataDrivenProfile() {
        BlockingProfileSnapshot profile = BlockingProfileSnapshot.fullBlock(77);
        SurvivalAction.RaiseShield action = new SurvivalAction.RaiseShield(
            0, true, true, true, 1d, 0f, 5, 5, 0, Optional.of(profile)
        );

        PlayerSnapshot applied = action.apply(player());

        assertTrue(applied.blocking().profile().isPresent());
        assertEquals(77, applied.blocking().profile().orElseThrow().remainingDurability());
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(), DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6), new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
