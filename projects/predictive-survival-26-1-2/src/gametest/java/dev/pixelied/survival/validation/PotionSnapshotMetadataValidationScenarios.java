package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.core.WorldSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

final class PotionSnapshotMetadataValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private PotionSnapshotMetadataValidationScenarios() {
    }

    static void validateObservablePotionTimingMetadata(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            ServerLevel level = (ServerLevel) player.level();

            Creeper owner = new Creeper(EntityType.CREEPER, level);
            owner.setNoAi(true);
            owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
            level.addFreshEntity(owner);

            ItemStack stack = new ItemStack(Items.LINGERING_POTION);
            stack.set(
                DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 200, 0))
            );
            stack.set(DataComponents.POTION_DURATION_SCALE, 0.5f);

            ThrownLingeringPotion potion = new ThrownLingeringPotion(level, owner, stack);
            potion.setNoGravity(true);
            potion.setPos(player.getX(), player.getEyeY(), player.getZ() + 5d);
            potion.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(potion);
            return new Setup(potion.getId(), owner.getId());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) != null);
            context.waitTick();
            context.waitTick();

            int clientAge = context.computeOnClient(minecraft -> {
                Entity entity = minecraft.level.getEntity(setup.projectileId());
                if (entity == null) throw new AssertionError("custom lingering potion disappeared before snapshot validation");
                return entity.tickCount;
            });
            SurvivalEngine.EngineFrame frame = context.computeOnClient(
                minecraft -> new MinecraftSurvivalRuntime(minecraft).capture()
            );
            WorldSnapshot.EntitySnapshot snapshot = frame.context().world().entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("custom lingering potion missing from production snapshot"));

            String age = snapshot.properties().get("projectile_age_ticks");
            if (age == null || Integer.parseInt(age) != clientAge) {
                throw new AssertionError(
                    "production snapshot did not preserve client projectile age; clientAge=" + clientAge
                        + " properties=" + snapshot.properties()
                );
            }

            String scale = snapshot.properties().get("potion_duration_scale");
            if (scale == null || Math.abs(Float.parseFloat(scale) - 0.5f) > EPSILON) {
                throw new AssertionError(
                    "production snapshot did not preserve POTION_DURATION_SCALE=0.5; properties="
                        + snapshot.properties()
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity projectile = player.level().getEntity(setup.projectileId());
                if (projectile != null) projectile.discard();
                Entity owner = player.level().getEntity(setup.ownerId());
                if (owner != null) owner.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
            });
            context.waitTick();
        }
    }

    private record Setup(int projectileId, int ownerId) {
    }
}
