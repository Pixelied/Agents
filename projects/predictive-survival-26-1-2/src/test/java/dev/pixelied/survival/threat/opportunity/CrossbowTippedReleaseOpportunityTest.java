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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossbowTippedReleaseOpportunityTest {
    @Test
    void visibleLoadedHarmingArrowPrearmsWhenBaselineArrowAloneIsNonlethal() {
        WorldSnapshot.EntitySnapshot attacker = new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            new Vec3Snapshot(0.9, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(0.6, 0.0, 0.0, 1.2, 1.8, 0.6),
            Map.of(
                "main_hand_item_key", "minecraft:crossbow",
                "offhand_item_key", "minecraft:air",
                "main_hand_crossbow_projectile_kind", "arrow",
                "main_hand_crossbow_arrow_instant_damage", "12.0",
                "line_of_sight", "true",
                "eye_position_x", "0.9",
                "eye_position_y", "1.62",
                "eye_position_z", "0.3"
            )
        );

        List<LethalOpportunity> opportunities = new ProjectileReleaseOpportunityPredictor().predict(
            context(attacker, 8f)
        );

        assertEquals(1, opportunities.size());
        LethalOpportunity opportunity = opportunities.getFirst();
        assertEquals("crossbow_tipped_harming", opportunity.evidence().get("release_family"));
        assertEquals("12.0", opportunity.evidence().get("crossbow_arrow_instant_damage"));
        assertEquals(12f, opportunity.projectedThreat().damage().rawDamage().max());
        assertTrue(opportunity.projectedThreat().damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertTrue(opportunity.projectedThreat().blockable(), "blocking the arrow must still suppress its potion follow-up");
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
