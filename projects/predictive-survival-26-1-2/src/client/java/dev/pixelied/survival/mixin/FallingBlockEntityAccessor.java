package dev.pixelied.survival.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityAccessor {
    @Accessor("hurtEntities")
    boolean predictiveSurvival$getHurtEntities();

    @Accessor("fallDamageMax")
    int predictiveSurvival$getFallDamageMax();

    @Accessor("fallDamagePerDistance")
    float predictiveSurvival$getFallDamagePerDistance();
}
