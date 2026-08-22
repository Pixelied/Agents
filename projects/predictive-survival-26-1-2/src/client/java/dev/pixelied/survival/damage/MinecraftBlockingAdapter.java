package dev.pixelied.survival.damage;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MinecraftBlockingAdapter {
    public BlockingSnapshot capture(LocalPlayer player) {
        if (player == null) throw new NullPointerException("player");
        if (!player.isUsingItem()) return BlockingSnapshot.none();

        ItemStack useItem = player.getUseItem();
        BlocksAttacks blocksAttacks = useItem.get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks == null) return BlockingSnapshot.none();

        int elapsed = player.getTicksUsingItem();
        int required = blocksAttacks.blockDelayTicks();
        return new BlockingSnapshot(true, 0f, elapsed, required, profile(useItem), 0);
    }

    public static Optional<BlockingProfileSnapshot> profile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        BlocksAttacks blocksAttacks = stack.get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks == null) return Optional.empty();

        List<BlockingProfileSnapshot.DamageReduction> reductions = new ArrayList<>();
        for (BlocksAttacks.DamageReduction reduction : blocksAttacks.damageReductions()) {
            Optional<Set<String>> types = reduction.type().map(MinecraftBlockingAdapter::damageTypeKeys);
            reductions.add(new BlockingProfileSnapshot.DamageReduction(
                reduction.horizontalBlockingAngle(), types, reduction.base(), reduction.factor()
            ));
        }
        BlocksAttacks.ItemDamageFunction itemDamage = blocksAttacks.itemDamage();
        int remainingDurability = stack.isDamageableItem()
            ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue())
            : Integer.MAX_VALUE;
        return Optional.of(new BlockingProfileSnapshot(
            reductions,
            new BlockingProfileSnapshot.ItemDamageFunction(itemDamage.threshold(), itemDamage.base(), itemDamage.factor()),
            blocksAttacks.bypassedBy().map(MinecraftBlockingAdapter::damageTypeKeys).orElse(Set.of()),
            blocksAttacks.disableCooldownScale(),
            remainingDurability
        ));
    }

    /** Legacy deterministic-fixture helper. */
    public static BlockingSnapshot snapshot(boolean using, int elapsed, int required, float guaranteedBlockedFraction) {
        return new BlockingSnapshot(using, guaranteedBlockedFraction, elapsed, required);
    }

    private static Set<String> damageTypeKeys(HolderSet<DamageType> types) {
        Set<String> keys = new LinkedHashSet<>();
        for (Holder<DamageType> type : types) keys.add(type.getRegisteredName());
        return Set.copyOf(keys);
    }
}
