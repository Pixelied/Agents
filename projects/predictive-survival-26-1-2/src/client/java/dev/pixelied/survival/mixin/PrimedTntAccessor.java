package dev.pixelied.survival.mixin;

import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PrimedTnt.class)
public interface PrimedTntAccessor {
    @Accessor("explosionPower")
    float predictiveSurvival$getExplosionPower();
}
