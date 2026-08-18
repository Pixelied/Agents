package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.timeline.ThreatEvent;
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
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

final class PotionValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private PotionValidationScenarios() {
    }

    static void validateSplashHarmingHasPreImpactThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            Creeper owner = new Creeper(EntityType.CREEPER, level);
            owner.setNoAi(true);
            owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
            level.addFreshEntity(owner);

            ItemStack stack = new ItemStack(Items.SPLASH_POTION);
            stack.set(
                DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1))
            );

            ThrownSplashPotion potion = new ThrownSplashPotion(level, owner, stack);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            potion.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(potion);
            return new Setup(potion.getId(), owner.getId());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(setup.projectileId()) != null);
            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent predicted = frame.timeline().events().stream()
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":"))
                .findFirst()
                .orElse(null);

            String snapshot = frame.context().world().entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                .map(entity -> "type=" + entity.typeKey() + " properties=" + entity.properties())
                .findFirst()
                .orElse("<projectile missing from snapshot>");

            float actualHealth = 20f;
            for (int tick = 0; tick < 30; tick++) {
                context.waitTick();
                actualHealth = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
                );
                if (actualHealth < 20f) break;
            }

            if (actualHealth >= 20f) {
                throw new AssertionError("splash Harming II fixture produced no server damage within 30 ticks; " + snapshot);
            }
            if (predicted == null) {
                throw new AssertionError(
                    "live splash Harming II caused server damage but production emitted no pre-impact threat; "
                        + "actualHealth=" + actualHealth + " " + snapshot
                );
            }

            float predictedHealth = new DamageSimulator().simulate(frame.context().player(), predicted.damage()).after().health();
            if (Math.abs(predictedHealth - actualHealth) > EPSILON) {
                throw new AssertionError(
                    "splash Harming II prediction did not match vanilla; predicted=" + predictedHealth
                        + " actual=" + actualHealth + " event=" + predicted + " " + snapshot
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                Entity projectile = level.getEntity(setup.projectileId());
                if (projectile != null) projectile.discard();
                Entity owner = level.getEntity(setup.ownerId());
                if (owner != null) owner.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
            });
            context.waitTick();
        }
    }

    private record Setup(int projectileId, int ownerId) {
    }
}
