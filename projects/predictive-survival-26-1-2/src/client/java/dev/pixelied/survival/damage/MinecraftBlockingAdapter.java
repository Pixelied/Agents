package dev.pixelied.survival.damage;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

public final class MinecraftBlockingAdapter {
    public BlockingSnapshot capture(LocalPlayer player) {
        if (player == null) throw new NullPointerException("player");
        if (!player.isUsingItem()) return BlockingSnapshot.none();

        ItemStack useItem = player.getUseItem();
        BlocksAttacks blocksAttacks = useItem.get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks == null) return BlockingSnapshot.none();

        int elapsed = player.getTicksUsingItem();
        int required = blocksAttacks.blockDelayTicks();

        // Source/direction-specific reductions are resolved later. Readiness is captured here,
        // but the generic snapshot must not invent a guaranteed block fraction.
        return snapshot(true, elapsed, required, 0f);
    }

    public static BlockingSnapshot snapshot(boolean using, int elapsed, int required, float guaranteedBlockedFraction) {
        return new BlockingSnapshot(using, guaranteedBlockedFraction, elapsed, required);
    }
}
