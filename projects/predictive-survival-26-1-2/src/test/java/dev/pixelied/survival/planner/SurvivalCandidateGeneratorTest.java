package dev.pixelied.survival.planner;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalCandidateGeneratorTest {
    private final SurvivalCandidateGenerator generator = new SurvivalCandidateGenerator();

    @Test
    void inventoryTotemCreatesOffhandSwapCandidate() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, slot(0, "minecraft:diamond_sword", false),
                10, slot(10, "minecraft:totem_of_undying", true),
                40, slot(40, "minecraft:shield", false)
            ),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36, 10, 10, 40, 45));

        List<SurvivalAction> candidates = generator.generate(context(DeathProtectionSnapshot.none(), BlockingSnapshot.none()), timeline(true), inventory, menu);

        SurvivalAction.EquipDeathProtection action = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            candidates.stream().filter(SurvivalAction.EquipDeathProtection.class::isInstance).findFirst().orElseThrow()
        );
        assertEquals(SurvivalAction.Hand.OFF_HAND, action.hand());
        assertEquals(0, action.requiredServerTicks());
    }

    @Test
    void activeOffhandShieldMakesInventoryTotemRouteMainhand() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, slot(0, "minecraft:diamond_sword", false),
                10, slot(10, "minecraft:totem_of_undying", true),
                40, slot(40, "minecraft:shield", false)
            ),
            true
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36, 10, 10, 40, 45));
        BlockingSnapshot active = new BlockingSnapshot(true, 0f, 5, 5);

        List<SurvivalAction> candidates = generator.generate(context(DeathProtectionSnapshot.none(), active), timeline(true), inventory, menu);

        SurvivalAction.EquipDeathProtection action = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            candidates.stream().filter(SurvivalAction.EquipDeathProtection.class::isInstance).findFirst().orElseThrow()
        );
        assertEquals(SurvivalAction.Hand.MAIN_HAND, action.hand());
        assertTrue(candidates.stream().anyMatch(SurvivalAction.RaiseShield.class::isInstance));
    }

    @Test
    void alreadyHeldDeathProtectionDoesNotCreateRedundantEquipCandidate() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(0, slot(0, "minecraft:totem_of_undying", true)),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36));

        List<SurvivalAction> candidates = generator.generate(
            context(DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()), BlockingSnapshot.none()),
            timeline(true),
            inventory,
            menu
        );

        assertTrue(candidates.stream().noneMatch(SurvivalAction.EquipDeathProtection.class::isInstance));
    }

    @Test
    void shieldCanBlockTheCurrentMeleeHitBeforeThatHitDisablesBlocking() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(0, slot(0, "minecraft:shield", false)),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36));
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:disabling_melee"
        );
        ThreatTimeline disablingMelee = new ThreatTimeline(List.of(new ThreatEvent(
            "disabling-melee", ThreatKind.MELEE, new TickWindow(6, 6), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, true
        )));

        List<SurvivalAction> candidates = generator.generate(
            context(DeathProtectionSnapshot.none(), BlockingSnapshot.none()),
            disablingMelee,
            inventory,
            menu
        );

        assertTrue(candidates.stream().anyMatch(SurvivalAction.RaiseShield.class::isInstance),
            "vanilla blocks the current melee hit before applying the blocking-item disable");
    }

    @Test
    void selectedBlockingItemOnServerCooldownCannotCreateRaiseShieldCandidate() {
        InventorySlotSnapshot coolingShield = blockingSlotOnCooldown(0, "minecraft:shield");
        InventorySnapshot inventory = new InventorySnapshot(0, Map.of(0, coolingShield), false);
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36));

        List<SurvivalAction> candidates = generator.generate(
            context(DeathProtectionSnapshot.none(), BlockingSnapshot.none()),
            timeline(true),
            inventory,
            menu
        );

        assertTrue(candidates.stream().noneMatch(SurvivalAction.RaiseShield.class::isInstance),
            "server-synchronized item cooldown must make the blocking item unavailable");
    }

    @Test
    void shieldCandidateIsRejectedWhenAnyThreatIsNotBlockable() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(0, slot(0, "minecraft:shield", false)),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36));

        List<SurvivalAction> candidates = generator.generate(context(DeathProtectionSnapshot.none(), BlockingSnapshot.none()), timeline(false), inventory, menu);

        assertTrue(candidates.stream().noneMatch(SurvivalAction.RaiseShield.class::isInstance));
    }

    @Test
    void heldGuaranteedConsumableEffectsCreateExecutableCandidate() {
        ConsumableSurvivalSnapshot consumable = new ConsumableSurvivalSnapshot(
            32,
            true,
            List.of(
                new EffectInstanceSnapshot("minecraft:resistance", 6000, 0),
                new EffectInstanceSnapshot("minecraft:absorption", 2400, 0)
            )
        );
        InventorySlotSnapshot held = new InventorySlotSnapshot(
            0,
            "test:survival_food",
            1,
            false,
            Optional.of(consumable),
            Optional.empty()
        );
        InventorySnapshot inventory = new InventorySnapshot(0, Map.of(0, held), false);
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36));

        List<SurvivalAction> candidates = generator.generate(
            context(DeathProtectionSnapshot.none(), BlockingSnapshot.none()),
            timeline(false, 40),
            inventory,
            menu
        );

        SurvivalAction.ApplyEffects action = assertInstanceOf(
            SurvivalAction.ApplyEffects.class,
            candidates.stream().filter(SurvivalAction.ApplyEffects.class::isInstance).findFirst().orElseThrow()
        );
        assertEquals("test:survival_food", action.itemKey());
        assertEquals(32, action.requiredServerTicks());
        assertEquals(0, action.statusEffectsAfter().resistanceAmplifier());
        assertEquals(4f, action.absorptionGain(), 0.0001f);
    }

    @Test
    void heldSwappableArmorCreatesRuntimeStatEquipmentCandidate() {
        ArmorPieceSnapshot chest = new ArmorPieceSnapshot(
            ArmorPieceSnapshot.Slot.CHEST,
            8f,
            3f,
            0,
            500,
            true
        );
        InventorySlotSnapshot held = new InventorySlotSnapshot(
            0,
            "test:heavy_chestplate",
            1,
            false,
            Optional.empty(),
            Optional.of(new EquippableSurvivalSnapshot(chest, true))
        );
        InventorySnapshot inventory = new InventorySnapshot(0, Map.of(0, held), false);
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36));

        List<SurvivalAction> candidates = generator.generate(
            context(DeathProtectionSnapshot.none(), BlockingSnapshot.none()),
            timeline(false, 5),
            inventory,
            menu
        );

        SurvivalAction.SwapEquipment action = assertInstanceOf(
            SurvivalAction.SwapEquipment.class,
            candidates.stream().filter(SurvivalAction.SwapEquipment.class::isInstance).findFirst().orElseThrow()
        );
        assertEquals("test:heavy_chestplate", action.equipmentUpdates().get("chest"));
        assertEquals(8f, action.mitigationAfter().armor(), 0.0001f);
        assertEquals(3f, action.mitigationAfter().toughness(), 0.0001f);
    }

    private static PredictionContext context(DeathProtectionSnapshot protection, BlockingSnapshot blocking) {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), blocking, HurtState.unknown(), protection,
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6), new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline(boolean blockable) {
        return timeline(blockable, 3);
    }

    private static ThreatTimeline timeline(boolean blockable, long impactTick) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:incoming"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "incoming", ThreatKind.OTHER, new TickWindow(impactTick, impactTick), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, blockable, true, false
        )));
    }

    private static InventorySlotSnapshot blockingSlotOnCooldown(int index, String key) {
        try {
            var constructor = InventorySlotSnapshot.class.getDeclaredConstructor(
                int.class, String.class, int.class, int.class, boolean.class,
                Optional.class, Optional.class, Optional.class, Optional.class, boolean.class
            );
            return constructor.newInstance(
                index, key, key.hashCode(), 1, false,
                Optional.empty(), Optional.empty(), Optional.of(BlockingProfileSnapshot.fullBlock(336)),
                Optional.empty(), true
            );
        } catch (ReflectiveOperationException missingCooldownState) {
            throw new AssertionError(
                "inventory slot snapshot cannot represent a client-observable blocking-item cooldown",
                missingCooldownState
            );
        }
    }

    private static InventorySlotSnapshot slot(int index, String key, boolean protection) {
        return new InventorySlotSnapshot(index, key, 1, protection);
    }
}
