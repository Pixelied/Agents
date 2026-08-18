package dev.adrien.crystaloptimizer.execution;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class HotbarRestockPlanner {
    private static final List<Item> PRIORITY = List.of(
        Items.END_CRYSTAL,
        Items.RESPAWN_ANCHOR,
        Items.GLOWSTONE,
        Items.OBSIDIAN
    );

    public Optional<RestockDecision> choose(List<InventoryStackView> stacks) {
        Objects.requireNonNull(stacks, "stacks");
        List<InventoryStackView> snapshot = List.copyOf(stacks);
        Set<Integer> occupiedHotbar = snapshot.stream()
            .filter(InventoryStackView::isHotbar)
            .map(InventoryStackView::playerInventorySlot)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        for (Item resource : PRIORITY) {
            Optional<InventoryStackView> reserve = snapshot.stream()
                .filter(InventoryStackView::isMainInventory)
                .filter(stack -> stack.item() == resource)
                .max(Comparator.comparingInt(InventoryStackView::count));
            if (reserve.isEmpty()) {
                continue;
            }

            List<InventoryStackView> ready = snapshot.stream()
                .filter(InventoryStackView::isHotbar)
                .filter(stack -> stack.item() == resource)
                .toList();
            if (ready.isEmpty()) {
                for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                    if (!occupiedHotbar.contains(hotbarSlot)) {
                        return Optional.of(new RestockDecision(
                            reserve.orElseThrow().playerInventorySlot(),
                            hotbarSlot,
                            resource
                        ));
                    }
                }
                continue;
            }

            int bestReadyCount = ready.stream().mapToInt(InventoryStackView::count).max().orElse(0);
            if (reserve.orElseThrow().count() <= bestReadyCount) {
                continue;
            }
            InventoryStackView target = ready.stream()
                .min(Comparator.comparingInt(InventoryStackView::count))
                .orElseThrow();
            return Optional.of(new RestockDecision(
                reserve.orElseThrow().playerInventorySlot(),
                target.playerInventorySlot(),
                resource
            ));
        }
        return Optional.empty();
    }
}
