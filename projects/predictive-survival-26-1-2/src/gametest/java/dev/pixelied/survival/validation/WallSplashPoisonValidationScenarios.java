package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class WallSplashPoisonValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double ANCHOR_TOLERANCE_SQUARED = 0.01d;

    private WallSplashPoisonValidationScenarios() {
    }

    static void validateWallSplashPoisonHasThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Vec3 clientAnchor = context.computeOnClient(minecraft -> minecraft.player.position());
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setNoGravity(false);
            player.setPos(clientAnchor.x, clientAnchor.y, clientAnchor.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.getFoodData().setFoodLevel(17);
            player.getFoodData().setSaturation(0f);
            ServerLevel level = (ServerLevel) player.level();

            BlockPos wallBase = player.blockPosition().offset(0, 0, 2);
            level.setBlockAndUpdate(wallBase, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(wallBase.above(), Blocks.STONE.defaultBlockState());

            Creeper owner = new Creeper(EntityType.CREEPER, level);
            owner.setNoAi(true);
            owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
            level.addFreshEntity(owner);

            ItemStack stack = new ItemStack(Items.SPLASH_POTION);
            stack.set(
                DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.POISON, 200, 0))
            );
            ThrownSplashPotion potion = new ThrownSplashPotion(level, owner, stack);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 5d);
            potion.setDeltaMovement(0d, 0d, -0.6d);
            level.addFreshEntity(potion);
            return new Setup(potion.getId(), owner.getId(), wallBase, clientAnchor);
        });

        try {
            anchorBoth(context, singleplayer, setup.playerAnchor());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.player != null
                && minecraft.player.position().distanceToSqr(setup.playerAnchor()) <= ANCHOR_TOLERANCE_SQUARED
                && minecraft.level.getEntity(setup.projectileId()) != null
                && minecraft.level.getBlockState(setup.wallBase()).is(Blocks.STONE)
                && minecraft.level.getBlockState(setup.wallBase().above()).is(Blocks.STONE));
            anchorBoth(context, singleplayer, setup.playerAnchor());

            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> new MinecraftSurvivalRuntime().capture());
            ThreatEvent predicted = frame.timeline().events().stream()
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":poison:"))
                .findFirst()
                .orElse(null);

            Observation firstPoison = null;
            DamageSample damage = null;
            int projectileGoneTick = -1;
            for (int tick = 1; tick <= 100; tick++) {
                anchorBoth(context, singleplayer, setup.playerAnchor());
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));
                if (!observation.projectilePresent() && projectileGoneTick < 0) projectileGoneTick = tick;
                if (observation.poisonDuration() >= 0 && firstPoison == null) firstPoison = observation;
                if (observation.health() < 20f - EPSILON) {
                    damage = new DamageSample(tick, observation);
                    break;
                }
            }

            if (firstPoison == null || damage == null) {
                throw new AssertionError(
                    "anchored wall-splash Poison fixture did not remain harmful; firstPoison=" + firstPoison
                        + " damage=" + damage + " projectileGoneTick=" + projectileGoneTick
                );
            }
            if (predicted == null) {
                throw new AssertionError(
                    "wall-splash Poison caused vanilla damage but production emitted no pre-impact poison threat; "
                        + "firstPoison=" + firstPoison
                        + " damage=" + damage
                        + " projectileGoneTick=" + projectileGoneTick
                        + " projectile=" + diagnostic(frame, setup.projectileId())
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
                level.setBlockAndUpdate(setup.wallBase(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(setup.wallBase().above(), Blocks.AIR.defaultBlockState());
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
                player.setNoGravity(false);
                player.setPos(setup.playerAnchor().x, setup.playerAnchor().y, setup.playerAnchor().z);
                player.setDeltaMovement(Vec3.ZERO);
            });
            anchorClient(context, setup.playerAnchor());
            context.waitTick();
        }
    }

    private static Observation observe(net.minecraft.server.MinecraftServer server, int projectileId) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        MobEffectInstance poison = player.getEffect(MobEffects.POISON);
        return new Observation(
            player.getHealth(),
            player.level().getEntity(projectileId) != null,
            poison == null ? -1 : poison.getDuration(),
            poison == null ? -1 : poison.getAmplifier(),
            String.valueOf(player.getLastDamageSource())
        );
    }

    private static void anchorBoth(ClientGameTestContext context, TestSingleplayerContext singleplayer, Vec3 anchor) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.setNoGravity(false);
            player.setPos(anchor.x, anchor.y, anchor.z);
            player.setDeltaMovement(Vec3.ZERO);
        });
        anchorClient(context, anchor);
    }

    private static void anchorClient(ClientGameTestContext context, Vec3 anchor) {
        context.computeOnClient(minecraft -> {
            if (minecraft.player != null) {
                minecraft.player.setNoGravity(false);
                minecraft.player.setPos(anchor.x, anchor.y, anchor.z);
                minecraft.player.setDeltaMovement(Vec3.ZERO);
            }
            return true;
        });
    }

    private static String diagnostic(SurvivalEngine.EngineFrame frame, int projectileId) {
        return frame.context().world().entities().stream()
            .filter(entity -> entity.id().equals(Integer.toString(projectileId)))
            .map(entity -> "type=" + entity.typeKey()
                + " position=" + entity.position()
                + " velocity=" + entity.velocity()
                + " properties=" + entity.properties())
            .findFirst()
            .orElse("<projectile missing from snapshot>");
    }

    private record Setup(int projectileId, int ownerId, BlockPos wallBase, Vec3 playerAnchor) {
    }

    private record Observation(
        float health,
        boolean projectilePresent,
        int poisonDuration,
        int poisonAmplifier,
        String lastDamageSource
    ) {
    }

    private record DamageSample(int tick, Observation observation) {
    }
}
