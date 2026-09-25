package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
abstract class ScoreboardMixin {
    /**
     * Vape's Scoreboard HUD locks the vanilla Objective score-list method while
     * its replacement HUD is enabled, then temporarily unlocks it while reading
     * the real rows for its own renderer.
     *
     * 26.2 overloads listPlayerScores. The recovered transformer targets the
     * Objective -> Collection<PlayerScoreEntry> overload used by the HUD.
     */
    @Inject(
            method = "listPlayerScores(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;",
            at = @At("HEAD"),
            cancellable = true)
    private void vape421$hideVanillaScores(
            Objective objective,
            CallbackInfoReturnable<Collection<PlayerScoreEntry>> cir) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventScoreboardScores",
                new Class<?>[0])) {
            cir.setReturnValue(new ArrayList<>());
        }
    }
}
