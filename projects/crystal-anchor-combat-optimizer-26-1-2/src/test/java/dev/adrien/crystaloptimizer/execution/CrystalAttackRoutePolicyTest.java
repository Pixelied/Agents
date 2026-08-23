package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CrystalAttackRoutePolicyTest {
    @Test
    void chosenCrystalAttackRouteAlwaysSatisfiesSourceBackedCapability() {
        var fixture = InteractionRouteFixtures.weaknessCrystalAttack();
        InteractionRoute route = new CrystalAttackRoutePolicy().route(
            fixture.inventory(),
            fixture.effects(),
            fixture.capability()
        ).orElseThrow();

        assertEquals(InteractionHand.MAIN_HAND, route.hand());
        assertTrue(route.selectedSlot().isPresent(),
            "weakness must move off the non-positive current attack route");
        assertTrue(fixture.capability().canDamageCrystal(
            fixture.itemProfile(route),
            fixture.effects()
        ));
    }

    @Test
    void noWeaknessKeepsAlreadySelectedMainhandInsteadOfPointlessToolSwap() {
        var fixture = InteractionRouteFixtures.weaknessCrystalAttack();
        InteractionRoute route = new CrystalAttackRoutePolicy().route(
            fixture.inventory(),
            StatusEffectSnapshot.none(),
            fixture.capability()
        ).orElseThrow();

        assertTrue(route.selectedSlot().isEmpty());
        assertTrue(fixture.capability().canDamageCrystal(
            fixture.itemProfile(route),
            StatusEffectSnapshot.none()
        ));
    }

    @Test
    void emptyMainhandStillUsesVanillaBaseAttackWhenNoWeaknessIsPresent() {
        InteractionRoute route = new CrystalAttackRoutePolicy().route(
            InventoryState.empty(),
            StatusEffectSnapshot.none(),
            CrystalAttackCapability.vanilla26_1_2()
        ).orElseThrow();

        assertEquals(InteractionHand.MAIN_HAND, route.hand());
        assertTrue(route.selectedSlot().isEmpty());
    }

    @Test
    void emptyMainhandUnderWeaknessNeedsARealPositiveDamageTool() {
        assertTrue(new CrystalAttackRoutePolicy().route(
            InventoryState.empty(),
            StatusEffectSnapshot.weakness(0),
            CrystalAttackCapability.vanilla26_1_2()
        ).isEmpty());
    }

    @Test
    void netheriteSpearRemainsAValidCrystalAttackRouteUnderWeaknessOne() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.NETHERITE_SPEAR, 1),
            Map.of(0, Items.NETHERITE_SPEAR),
            Map.of(0, 1),
            Optional.empty()
        );
        CrystalAttackCapability capability = CrystalAttackCapability.vanilla26_1_2();
        InteractionRoute route = new CrystalAttackRoutePolicy().route(
            inventory,
            StatusEffectSnapshot.weakness(0),
            capability
        ).orElseThrow();

        assertEquals(InteractionHand.MAIN_HAND, route.hand());
        assertTrue(capability.canDamageCrystal(
            AttackItemProfile.fromVanillaItem(Items.NETHERITE_SPEAR),
            StatusEffectSnapshot.weakness(0)
        ));
    }
}
