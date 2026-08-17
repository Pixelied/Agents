package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.Vec3Snapshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public final class MinecraftDamageAdapter {
    public DamageSourceSnapshot snapshot(DamageSource source, float rawDamage, LocalPlayer player) {
        if (source == null) throw new NullPointerException("source");
        if (player == null) throw new NullPointerException("player");

        EnumSet<DamageFlag> flags = EnumSet.noneOf(DamageFlag.class);
        for (DamageFlag flag : DamageFlag.values()) {
            if (source.is(tagFor(flag))) flags.add(flag);
        }

        float freezingMultiplier = source.is(DamageTypeTags.IS_FREEZING)
            && player.getType().builtInRegistryHolder().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)
            ? 5f
            : 1f;
        Vec3 sourcePosition = source.getSourcePosition();
        boolean piercingProjectile = source.getDirectEntity() instanceof AbstractArrow arrow
            && piercingProjectile(true, arrow.getPierceLevel());

        return new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            flags,
            source.scalesWithDifficulty(),
            freezingMultiplier,
            piercingProjectile,
            sourcePosition == null
                ? Optional.empty()
                : Optional.of(new Vec3Snapshot(sourcePosition.x(), sourcePosition.y(), sourcePosition.z())),
            source.typeHolder().getRegisteredName()
        );
    }

    static boolean piercingProjectile(boolean directEntityIsArrow, int pierceLevel) {
        return directEntityIsArrow && pierceLevel > 0;
    }

    public static TagKey<DamageType> tagFor(DamageFlag flag) {
        return switch (flag) {
            case DAMAGES_HELMET -> DamageTypeTags.DAMAGES_HELMET;
            case BYPASSES_ARMOR -> DamageTypeTags.BYPASSES_ARMOR;
            case BYPASSES_SHIELD -> DamageTypeTags.BYPASSES_SHIELD;
            case BYPASSES_INVULNERABILITY -> DamageTypeTags.BYPASSES_INVULNERABILITY;
            case BYPASSES_COOLDOWN -> DamageTypeTags.BYPASSES_COOLDOWN;
            case BYPASSES_EFFECTS -> DamageTypeTags.BYPASSES_EFFECTS;
            case BYPASSES_RESISTANCE -> DamageTypeTags.BYPASSES_RESISTANCE;
            case BYPASSES_ENCHANTMENTS -> DamageTypeTags.BYPASSES_ENCHANTMENTS;
            case IS_FIRE -> DamageTypeTags.IS_FIRE;
            case IS_PROJECTILE -> DamageTypeTags.IS_PROJECTILE;
            case IS_EXPLOSION -> DamageTypeTags.IS_EXPLOSION;
            case IS_FALL -> DamageTypeTags.IS_FALL;
            case IS_DROWNING -> DamageTypeTags.IS_DROWNING;
            case IS_FREEZING -> DamageTypeTags.IS_FREEZING;
            case IS_LIGHTNING -> DamageTypeTags.IS_LIGHTNING;
            case IS_MACE_SMASH -> DamageTypeTags.IS_MACE_SMASH;
        };
    }
}
