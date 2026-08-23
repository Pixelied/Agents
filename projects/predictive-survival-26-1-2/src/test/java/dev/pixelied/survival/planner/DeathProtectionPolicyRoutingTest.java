package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DeathProtectionPolicyRoutingTest {
    private final SurvivalCandidateGenerator generator = new SurvivalCandidateGenerator();

    @Test
    void inventoryRoutingOffNeverPullsTotemFromAnotherSlot() {
        RescuePolicy policy = new RescuePolicy(true, false, false, false, false, true, false);

        List<SurvivalAction> candidates = generator.generate(
            context(), timeline(), inventory(false), menu(), policy
        );

        assertFalse(candidates.stream().anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance));
    }

    @Test
    void mainHandTakeoverOffRoutesInventoryTotemToOffhand() {
        RescuePolicy policy = new RescuePolicy(true, false, false, false, true, false, false);

        SurvivalAction.EquipDeathProtection protection = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            generator.generate(context(), timeline(), inventory(false), menu(), policy).stream()
                .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                .findFirst()
                .orElseThrow()
        );

        assertEquals(SurvivalAction.Hand.OFF_HAND, protection.hand());
    }

    @Test
    void mainHandTakeoverOnMaySelectHotbarTotemForFasterProtection() {
        RescuePolicy policy = new RescuePolicy(true, false, false, false, true, true, false);

        SurvivalAction.EquipDeathProtection protection = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            generator.generate(context(), timeline(), inventory(false), menu(), policy).stream()
                .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                .findFirst()
                .orElseThrow()
        );

        assertEquals(SurvivalAction.Hand.MAIN_HAND, protection.hand());
    }

    private static InventorySnapshot inventory(boolean activeOffhandShield) {
        return new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                2, new InventorySlotSnapshot(2, "minecraft:totem_of_undying", 202, 1, true,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, 0, false,
                    Optional.empty(), Optional.empty(), Optional.empty())
            ),
            activeOffhandShield
        );
    }

    private static MenuSlotMap menu() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 2, 38, 40, 45));
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            4f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(20f), Set.of(), false, 1f, false, Optional.empty(), "test:lethal"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "lethal", ThreatKind.OTHER, new TickWindow(20, 20), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), false, false, false, false
        )));
    }
}
