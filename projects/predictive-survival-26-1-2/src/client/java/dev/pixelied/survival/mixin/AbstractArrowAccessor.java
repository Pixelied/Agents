package dev.pixelied.survival.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Accessor("baseDamage")
    double predictiveSurvival$getBaseDamage();

    @Invoker("isInGround")
    boolean predictiveSurvival$isInGround();
}
