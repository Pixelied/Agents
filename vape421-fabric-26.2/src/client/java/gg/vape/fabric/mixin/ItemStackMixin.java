package gg.vape.fabric.mixin;

import gg.vape.fabric.tooltip.RecoveredTooltipAdapter;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true)
    private void vape421$tooltip(
            Item.TooltipContext context,
            @Nullable Player player,
            TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(RecoveredTooltipAdapter.rewrite(
                this, player, cir.getReturnValue()));
    }
}
