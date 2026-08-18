package dev.pixelied.survival.validation;

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

final class PoisonPotionValidationScenarios {
    private PoisonPotionValidationScenarios() {
    }

    static void validateSplashPoisonHasPreImpactThreat(
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
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.POISON, 100, 0))
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
            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent predicted = frame.timeline().events().stream()
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":"))
                .findFirst()
                .orElse(null);
            String snapshot = frame.context().world().entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                .map(entity -> "type=" + entity.typeKey()
                    + " position=" + entity.position()
                    + " velocity=" + entity.velocity()
                    + " properties=" + entity.properties())
                .findFirst()
                .orElse("<projectile missing from snapshot>");

            Observation firstPoison = null;
            Observation firstDamage = null;
            int projectileGoneTick = -1;
            for (int tick = 1; tick <= 100; tick++) {
                anchor(singleplayer, setup.playerAnchor());
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));
                if (!observation.projectilePresent() && projectileGoneTick < 0) projectileGoneTick = tick;
                if (firstPoison == null && observation.poisonDuration() >= 0) firstPoison = observation;
                if (observation.health() < 20f) {
                    firstDamage = observation;
                    break;
                }
            }

            if (firstPoison == null) {
                throw new AssertionError(
                    "splash Poison fixture never applied Poison within 100 ticks; "
                        + snapshot + " projectileGoneTick=" + projectileGoneTick
                );
            }
            if (firstDamage == null) {
                throw new AssertionError(
                    "splash Poison fixture applied Poison but caused no vanilla damage within 100 ticks; "
                        + snapshot + " projectileGoneTick=" + projectileGoneTick
                        + " firstPoison=" + firstPoison
                );
            }
            if (predicted == null) {
                throw new AssertionError(
                    "live splash Poison caused vanilla damage but production emitted no pre-impact projectile threat; "
                        + snapshot + " projectileGoneTick=" + projectileGoneTick
                        + " firstPoison=" + firstPoison + " firstDamage=" + firstDamage
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

    private static void anchor(TestSingleplayerContext singleplayer, Vec3 anchor) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.setPos(anchor.x, anchor.y, anchor.z);
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static Observation observe(net.minecraft.server.MinecraftServer server, int projectileId) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        MobEffectInstance poison = player.getEffect(MobEffects.POISON);
        return new Observation(
            player.getHealth(),
            player.level().getEntity(projectileId) != null,
            poison == null ? -1 : poison.getDuration(),
            poison == null ? -1 : poison.getAmplifier()
        );
    }

    private record Setup(int projectileId, int ownerId, Vec3 playerAnchor) {
    }

    private record Observation(
        float health,
        boolean projectilePresent,
        int poisonDuration,
        int poisonAmplifier
    ) {
    }
}
