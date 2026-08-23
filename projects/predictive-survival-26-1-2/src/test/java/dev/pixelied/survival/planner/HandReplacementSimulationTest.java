package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.ArmorPieceSnapshot;
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        PlayerSnapshot player = player(
            activeShield,
            DeathProtectionSnapshot.none(),
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

    @Test
    void routedMainhandShieldReplacesMainhandDeathProtectionCapability() {
        PlayerSnapshot player = player(
            BlockingSnapshot.none(),
            DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            Map.of("mainhand", "minecraft:totem_of_undying")
        );
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333, 3
        );
        SurvivalAction.RaiseShield shield = new SurvivalAction.RaiseShield(
            8, true, true, true, 1d, 0f, 0, 5, 2,
            Optional.of(BlockingProfileSnapshot.fullBlock(336)),
            Optional.of(new SurvivalAction.HeldItemRef(
                SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333, Optional.of(route)
            ))
        );

        PlayerSnapshot after = shield.apply(player);

        assertFalse(after.deathProtection().mainHandAvailable(),
            "routing a shield into the main hand must displace protection previously held there");
        assertTrue(after.blocking().active());
        assertEquals("minecraft:shield", after.equipmentItemKeys().get("mainhand"));
    }

    @Test
    void routedMainhandShieldThenMainhandTotemCannotKeepShieldBlocking() {
        PlayerSnapshot initial = player(
            BlockingSnapshot.none(),
            DeathProtectionSnapshot.none(),
            Map.of("mainhand", "minecraft:diamond_sword")
        );
        SurvivalItemRoute.ContainerSwap shieldRoute = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333, 3
        );
        SurvivalAction.RaiseShield shield = new SurvivalAction.RaiseShield(
            8, true, true, true, 1d, 0f, 0, 5, 2,
            Optional.of(BlockingProfileSnapshot.fullBlock(336)),
            Optional.of(new SurvivalAction.HeldItemRef(
                SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333, Optional.of(shieldRoute)
            ))
        );
        SurvivalAction.EquipDeathProtection totem = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.MAIN_HAND,
            2, true, true, 1d, 1, 2
        );

        PlayerSnapshot afterShield = shield.apply(initial);
        PlayerSnapshot afterTotem = totem.apply(afterShield);

        assertTrue(afterShield.blocking().active());
        assertFalse(afterTotem.blocking().active(),
            "a later main-hand Totem cannot coexist with the routed main-hand shield's blocking state");
        assertTrue(afterTotem.deathProtection().mainHandAvailable());
    }

    @Test
    void mainhandConsumableRouteDisplacesMainhandDeathProtection() {
        PlayerSnapshot player = player(
            BlockingSnapshot.none(),
            DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            Map.of("mainhand", "minecraft:totem_of_undying")
        );
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 444, 3
        );
        EffectInstanceSnapshot resistance = new EffectInstanceSnapshot("minecraft:resistance", 200, 0);
        SurvivalAction.ApplyEffects consume = new SurvivalAction.ApplyEffects(
            StatusEffectsSnapshot.none().apply(List.of(resistance)),
            0f, 0f, "minecraft:potion", 35, true, true, 1d, 1, 4,
            Optional.of(new SurvivalAction.HeldItemRef(
                SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 444, Optional.of(route)
            )),
            List.of(resistance), -1f
        );

        PlayerSnapshot after = consume.apply(player);

        assertFalse(after.deathProtection().mainHandAvailable(),
            "a routed consumable cannot leave a displaced main-hand Totem armed in simulation");
    }

    @Test
    void mainhandEquipmentRouteDisplacesMainhandDeathProtection() {
        PlayerSnapshot player = player(
            BlockingSnapshot.none(),
            DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            Map.of("mainhand", "minecraft:totem_of_undying")
        );
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:netherite_chestplate", 555, 3
        );
        ArmorPieceSnapshot chest = new ArmorPieceSnapshot(
            ArmorPieceSnapshot.Slot.CHEST, 8f, 3f, 0, 500, true
        );
        SurvivalAction.SwapEquipment equip = new SurvivalAction.SwapEquipment(
            MitigationSnapshot.none(),
            Map.of("chest", "minecraft:netherite_chestplate"),
            3, true, true, 1d, 0, 5,
            Optional.of(new SurvivalAction.HeldItemRef(
                SurvivalAction.Hand.MAIN_HAND, "minecraft:netherite_chestplate", 555, Optional.of(route)
            )),
            Optional.of(chest)
        );

        PlayerSnapshot after = equip.apply(player);

        assertFalse(after.deathProtection().mainHandAvailable(),
            "equipping a routed chestplate cannot leave the displaced main-hand Totem armed in simulation");
    }

    private static PlayerSnapshot player(
        BlockingSnapshot blocking,
        DeathProtectionSnapshot protection,
        Map<String, String> equipment
    ) {
        return new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            blocking,
            HurtState.unknown(),
            protection,
            new AabbSnapshot(0, 64, 0, 0.6, 65.8, 0.6),
            new Vec3Snapshot(0, 64, 0),
            new Vec3Snapshot(0, 0, 0),
            equipment
        );
    }
}
