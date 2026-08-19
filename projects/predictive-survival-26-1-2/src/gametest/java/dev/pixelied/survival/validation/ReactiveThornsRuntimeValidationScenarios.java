package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

final class ReactiveThornsRuntimeValidationScenarios {
    private ReactiveThornsRuntimeValidationScenarios() {
    }

    static void validateVisibleTwoPieceThornsReachesProductionRuntime(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int targetId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            var thorns = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.THORNS);

            ArmorStand target = new ArmorStand(level, player.getX() + 2d, player.getY(), player.getZ());
            target.setNoGravity(true);
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            helmet.enchant(thorns, 2);
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chest.enchant(thorns, 3);
            target.setItemSlot(EquipmentSlot.HEAD, helmet);
            target.setItemSlot(EquipmentSlot.CHEST, chest);
            level.addFreshEntity(target);
            return target.getId();
        });

        try {
            context.waitFor(minecraft -> {
                if (minecraft.level == null) return false;
                Entity entity = minecraft.level.getEntity(targetId);
                if (!(entity instanceof LivingEntity living)) return false;
                var enchantments = living.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                var thorns = enchantments.getOrThrow(Enchantments.THORNS);
                return EnchantmentHelper.getItemEnchantmentLevel(thorns, living.getItemBySlot(EquipmentSlot.HEAD)) == 2
                    && EnchantmentHelper.getItemEnchantmentLevel(thorns, living.getItemBySlot(EquipmentSlot.CHEST)) == 3;
            });

            long thornsThreats = context.computeOnClient(minecraft -> {
                if (minecraft.level == null) throw new AssertionError("client level unavailable for Thorns validation");
                Entity target = minecraft.level.getEntity(targetId);
                if (!(target instanceof LivingEntity living)) {
                    throw new AssertionError("client did not retain visible Thorns target");
                }
                var previousHitResult = minecraft.hitResult;
                try {
                    minecraft.hitResult = new EntityHitResult(living);
                    return new MinecraftSurvivalRuntime(minecraft).capture().timeline().events().stream()
                        .filter(event -> "minecraft:thorns".equals(event.damage().sourceKey()))
                        .count();
                } finally {
                    minecraft.hitResult = previousHitResult;
                }
            });

            if (thornsThreats != 2L) {
                throw new AssertionError(
                    "production runtime expected two independent visible Thorns threats, found " + thornsThreats
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity target = player.level().getEntity(targetId);
                if (target != null) target.discard();
            });
            context.waitTick();
        }
    }
}
