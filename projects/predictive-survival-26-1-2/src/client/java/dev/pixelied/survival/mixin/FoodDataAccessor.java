package dev.pixelied.survival.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodData.class)
public interface FoodDataAccessor {
    @Accessor("tickTimer")
    int predictiveSurvival$getTickTimer();
}
