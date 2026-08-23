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
import dev.pixelied.survival.damage.ArmorPieceSnapshot;
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.ConsumableSurvivalSnapshot;
import dev.pixelied.survival.inventory.EquippableSurvivalSnapshot;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalCandidatePolicyTest {
    private final SurvivalCandidateGenerator generator = new SurvivalCandidateGenerator();

    @Test
    void totemOnlyNeverGeneratesShieldConsumableOrEquipmentActions() {
        List<SurvivalAction> candidates = generator.generate(
            context(),
            timeline(),
            inventoryWithEveryRescueFamily(),
            menu(),
            RescuePolicy.totemOnly()
        );

        assertTrue(candidates.stream().anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance));
        assertFalse(candidates.stream().anyMatch(SurvivalAction.RaiseShield.class::isInstance));
        assertFalse(candidates.stream().anyMatch(SurvivalAction.ApplyEffects.class::isInstance));
        assertFalse(candidates.stream().anyMatch(SurvivalAction.SwapEquipment.class::isInstance));
    }

    @Test
    void totemAndShieldNeverGeneratesConsumableOrEquipmentActions() {
        List<SurvivalAction> candidates = generator.generate(
            context(),
            timeline(),
            inventoryWithEveryRescueFamily(),
            menu(),
            RescuePolicy.totemAndShield()
        );

        assertTrue(candidates.stream().anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance));
        assertTrue(candidates.stream().anyMatch(SurvivalAction.RaiseShield.class::isInstance));
        assertFalse(candidates.stream().anyMatch(SurvivalAction.ApplyEffects.class::isInstance));
        assertFalse(candidates.stream().anyMatch(SurvivalAction.SwapEquipment.class::isInstance));
    }

    @Test
    void customPolicyCanAllowConsumablesWhileForbiddingDeathProtection() {
        RescuePolicy policy = new RescuePolicy(false, false, true, false, false, false, false);
        List<SurvivalAction> candidates = generator.generate(
            context(), timeline(), inventoryWithEveryRescueFamily(), menu(), policy
        );

        assertFalse(candidates.stream().anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance));
        assertTrue(candidates.stream().anyMatch(SurvivalAction.ApplyEffects.class::isInstance));
        assertFalse(candidates.stream().anyMatch(SurvivalAction.RaiseShield.class::isInstance));
        assertFalse(candidates.stream().anyMatch(SurvivalAction.SwapEquipment.class::isInstance));
    }

    private static InventorySnapshot inventoryWithEveryRescueFamily() {
        ConsumableSurvivalSnapshot consumable = new ConsumableSurvivalSnapshot(
            32, true, List.of(new EffectInstanceSnapshot("minecraft:resistance", 200, 0))
        );
        ArmorPieceSnapshot armor = new ArmorPieceSnapshot(
            ArmorPieceSnapshot.Slot.CHEST, 8f, 3f, 0, 500, true
        );
        return new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(
                    0, "test:survival_food", 1, false, Optional.of(consumable), Optional.empty()
                ),
                1, new InventorySlotSnapshot(1, "minecraft:totem_of_undying", 1, true),
                2, new InventorySlotSnapshot(
                    2, "test:emergency_chestplate", 1, false, Optional.empty(),
                    Optional.of(new EquippableSurvivalSnapshot(armor, true))
                ),
                40, new InventorySlotSnapshot(
                    40, "minecraft:shield", "minecraft:shield".hashCode(), 1, false,
                    Optional.empty(), Optional.empty(), Optional.of(BlockingProfileSnapshot.fullBlock(336))
                )
            ),
            true
        );
    }

    private static MenuSlotMap menu() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 1, 37, 2, 38, 40, 45));
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:incoming"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "incoming", ThreatKind.OTHER, new TickWindow(40, 40), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, false
        )));
    }
}
