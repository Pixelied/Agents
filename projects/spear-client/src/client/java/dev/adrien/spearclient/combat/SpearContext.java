package dev.adrien.spearclient.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public record SpearContext(
    Vec3 origin,
    Vec3 eye,
    Vec3 look,
    float yaw,
    float pitch,
    boolean onGround,
    boolean horizontalCollision,
    ItemStack spear,
    PiercingWeapon piercing,
    KineticWeapon kinetic,
    int ticksUsingItem,
    int targetId,
    Vec3 targetPosition,
    int lungeLevel,
    boolean vanillaLungeEligible
) {
    public SpearContext(
        Vec3 origin,
        Vec3 eye,
        Vec3 look,
        float yaw,
        float pitch,
        boolean onGround,
        boolean horizontalCollision,
        ItemStack spear,
        PiercingWeapon piercing,
        KineticWeapon kinetic,
        int ticksUsingItem,
        int targetId,
        Vec3 targetPosition
    ) {
        this(
            origin,
            eye,
            look,
            yaw,
            pitch,
            onGround,
            horizontalCollision,
            spear,
            piercing,
            kinetic,
            ticksUsingItem,
            targetId,
            targetPosition,
            0,
            false
        );
    }

    public static SpearContext capture(Minecraft client, Player target) {
        Player player = client.player;
        if (player == null || client.level == null || target == null) {
            return null;
        }

        ItemStack held = player.getMainHandItem();
        PiercingWeapon piercing = held.get(DataComponents.PIERCING_WEAPON);
        if (piercing == null) {
            return null;
        }

        KineticWeapon kinetic = held.get(DataComponents.KINETIC_WEAPON);
        Holder<Enchantment> lunge = client.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.LUNGE);
        int lungeLevel = EnchantmentHelper.getItemEnchantmentLevel(lunge, held);
        boolean vanillaLungeEligible = lungeLevel > 0
            && !player.isPassenger()
            && !player.isFallFlying()
            && !player.isInWater()
            && (player.isCreative() || player.getFoodData().getFoodLevel() >= 7);

        return new SpearContext(
            player.position(),
            player.getEyePosition(),
            player.getLookAngle(),
            player.getYRot(),
            player.getXRot(),
            player.onGround(),
            player.horizontalCollision,
            held.copy(),
            piercing,
            kinetic,
            player.getTicksUsingItem(),
            target.getId(),
            target.position(),
            lungeLevel,
            vanillaLungeEligible
        );
    }
}
