package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class InteractionRouteFixtures {
    public static OffhandCrystalPlacement offhandCrystalPlacement() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.DIAMOND_SWORD, 1, Items.END_CRYSTAL, 1),
            Map.of(0, Items.DIAMOND_SWORD),
            Map.of(0, 1),
            Optional.of(Items.END_CRYSTAL)
        );
        return new OffhandCrystalPlacement(
            new PlaceCrystal(new BlockPos(1, 64, 1)),
            inventory,
            new PendingItemLedger(),
            OptimizerConfig.defaults()
        );
    }

    public static WeaknessCrystalAttack weaknessCrystalAttack() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.END_CRYSTAL, 1, Items.DIAMOND_SWORD, 1),
            Map.of(0, Items.END_CRYSTAL, 1, Items.DIAMOND_SWORD),
            Map.of(0, 1, 1, 1),
            Optional.empty()
        );
        return new WeaknessCrystalAttack(
            inventory,
            StatusEffectSnapshot.weakness(0),
            CrystalAttackCapability.vanilla26_1_2()
        );
    }

    public record OffhandCrystalPlacement(
        PlaceCrystal action,
        InventoryState inventory,
        PendingItemLedger ledger,
        OptimizerConfig config
    ) {
    }

    public record WeaknessCrystalAttack(
        InventoryState inventory,
        StatusEffectSnapshot effects,
        CrystalAttackCapability capability
    ) {
        public AttackItemProfile itemProfile(InteractionRoute route) {
            Item item = route.selectedSlot().isPresent()
                ? inventory.hotbarItems().get(route.selectedSlot().getAsInt())
                : inventory.selectedItem().orElse(Items.AIR);
            return AttackItemProfile.fromVanillaItem(item);
        }
    }

    private InteractionRouteFixtures() {
    }
}
