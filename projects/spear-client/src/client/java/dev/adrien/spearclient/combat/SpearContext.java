package dev.adrien.spearclient.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
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
    Vec3 targetPosition
) {
    public static SpearContext capture(Minecraft client, Player target) {
        Player player = client.player;
        if (player == null || target == null) {
            return null;
        }

        ItemStack held = player.getMainHandItem();
        PiercingWeapon piercing = held.get(DataComponents.PIERCING_WEAPON);
        if (piercing == null) {
            return null;
        }

        KineticWeapon kinetic = held.get(DataComponents.KINETIC_WEAPON);
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
            target.position()
        );
    }
}
