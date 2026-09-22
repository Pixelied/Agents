package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
abstract class ScoreboardMixin {
    /**
     * The recovered 4.21 callback returned a raw ArrayList here, which is not
     * type-correct on 26.2. The actual feature is a lock used by Vape's custom
     * scoreboard HUD to suppress vanilla score rows while temporarily
     * unlocking this method to read the real data itself.
     */
    @Inject(method = "listPlayerScores", at = @At("HEAD"), cancellable = true)
    private void vape421$hideVanillaScores(
            ScoreHolder player,
            CallbackInfoReturnable<Object2IntMap<Objective>> cir) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventScoreboardScores",
                new Class<?>[0])) {
            cir.setReturnValue(new Object2IntOpenHashMap<>());
        }
    }
}
