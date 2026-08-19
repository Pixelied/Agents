package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
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

final class WitherPersistenceValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private WitherPersistenceValidationScenarios() {
    }

    static void validateActiveWitherRetainsFutureThreat(
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
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 100, 0))
            );
            ThrownSplashPotion potion = new ThrownSplashPotion(level, owner, stack);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            potion.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(potion);
            return new Setup(potion.getId(), owner.getId(), player.position());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) != null);
            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame beforeImpact = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent firstForecast = beforeImpact.timeline().events().stream()
                .filter(event -> event.id().equals("projectile:" + setup.projectileId() + ":wither:0"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("splash Wither had no pre-impact wither:0 event"));
            assertWitherEvent("pre-impact Wither", firstForecast);

            for (int tick = 1; tick <= 12; tick++) {
                anchor(singleplayer, setup.playerAnchor());
                context.waitTick();
                boolean projectilePresent = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).level().getEntity(setup.projectileId()) != null
                );
                if (!projectilePresent) break;
            }

            context.waitFor(minecraft -> minecraft.player != null && minecraft.player.hasEffect(MobEffects.WITHER));
            SurvivalEngine.EngineFrame active = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent persistent = active.timeline().events().stream()
                .filter(event -> event.id().startsWith("env:wither:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "active Wither had no future env:wither threat after projectile disappearance; status="
                        + active.context().player().statusEffects()
                ));

            assertWitherEvent("active Wither", persistent);
            if (persistent.impact().earliest() < 1L || persistent.impact().latest() > 40L) {
                throw new AssertionError(
                    "active Wither next tick should remain within one vanilla 40-tick cadence; event=" + persistent
                        + " status=" + active.context().player().statusEffects()
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
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static void assertWitherEvent(String label, ThreatEvent event) {
        if (!DamageRange.exact(1f).equals(event.damage().rawDamage())) {
            throw new AssertionError(label + " expected 1 raw damage, got " + event.damage().rawDamage());
        }
        if (!"minecraft:wither".equals(event.damage().sourceKey())) {
            throw new AssertionError(label + " expected minecraft:wither, got " + event.damage().sourceKey());
        }
        if (Math.abs(event.damage().applicationHealthThresholdExclusive()) > EPSILON) {
            throw new AssertionError(
                label + " must not inherit Poison's health floor, got "
                    + event.damage().applicationHealthThresholdExclusive()
            );
        }
    }

    private static void anchor(TestSingleplayerContext singleplayer, Vec3 anchor) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.setPos(anchor.x, anchor.y, anchor.z);
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private record Setup(int projectileId, int ownerId, Vec3 playerAnchor) {
    }
}
