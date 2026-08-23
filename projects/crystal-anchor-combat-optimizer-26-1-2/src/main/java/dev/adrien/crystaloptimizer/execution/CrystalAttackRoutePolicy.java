package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Chooses the least disruptive main-hand route that can positively damage an end crystal. */
public final class CrystalAttackRoutePolicy {
    public Optional<InteractionRoute> route(
        InventoryState inventory,
        StatusEffectSnapshot effects,
        CrystalAttackCapability capability
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(capability, "capability");

        Optional<Item> selected = inventory.selectedItem();
        if (selected.isPresent()
            && capability.canDamageCrystal(
                AttackItemProfile.fromVanillaItem(selected.orElseThrow()),
                effects
            )) {
            return Optional.of(InteractionRoute.selectedMainhand());
        }
        if (selected.isEmpty()
            && capability.canDamageCrystal(AttackItemProfile.fromVanillaItem(Items.AIR), effects)) {
            return Optional.of(InteractionRoute.selectedMainhand());
        }

        int selectedSlot = inventory.selectedHotbarSlot();
        return inventory.hotbarItems().entrySet().stream()
            .filter(entry -> inventory.hotbarCount(entry.getKey()) > 0)
            .filter(entry -> capability.canDamageCrystal(
                AttackItemProfile.fromVanillaItem(entry.getValue()),
                effects
            ))
            .sorted(Comparator
                .comparingInt((Map.Entry<Integer, Item> entry) ->
                    Math.abs(entry.getKey() - selectedSlot))
                .thenComparingInt(Map.Entry::getKey))
            .findFirst()
            .map(entry -> InteractionRoute.selectMainhand(entry.getKey(), 0.0));
    }
}
