package dev.pixelied.survival.mixin;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AreaEffectCloud.class)
public interface AreaEffectCloudAccessor {
    @Accessor("potionContents")
    PotionContents predictiveSurvival$getPotionContents();

    @Accessor("potionDurationScale")
    float predictiveSurvival$getPotionDurationScale();

    @Accessor("reapplicationDelay")
    int predictiveSurvival$getReapplicationDelay();
}
