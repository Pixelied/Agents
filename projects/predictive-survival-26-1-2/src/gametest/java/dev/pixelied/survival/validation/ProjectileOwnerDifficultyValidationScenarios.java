package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.WorldSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ProjectileOwnerDifficultyValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();

    private ProjectileOwnerDifficultyValidationScenarios() {
    }

    static void validateMobOwnedScalingMetadata(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validateLlamaSpit(context, singleplayer);
        validateWindCharge(context, singleplayer);
        validateFirework(context, singleplayer);
        validateSplashPotion(context, singleplayer);
    }

    private static void validateLlamaSpit(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = owner(level, player);
            LlamaSpit projectile = new LlamaSpit(EntityType.LLAMA_SPIT, level);
            prepare(projectile, player, owner);
            level.addFreshEntity(projectile);
            return new Setup(
                projectile.getId(),
                owner.getId(),
                player.damageSources().spit(projectile, owner).scalesWithDifficulty()
            );
        });
        validateSnapshot(context, singleplayer, setup, "llama_spit");
    }

    private static void validateWindCharge(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = owner(level, player);
            BreezeWindCharge projectile = new BreezeWindCharge(EntityType.BREEZE_WIND_CHARGE, level);
            prepare(projectile, player, owner);
            level.addFreshEntity(projectile);
            return new Setup(
                projectile.getId(),
                owner.getId(),
                player.damageSources().windCharge(projectile, owner).scalesWithDifficulty()
            );
        });
        validateSnapshot(context, singleplayer, setup, "breeze_wind_charge");
    }

    private static void validateFirework(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = owner(level, player);
            FireworkRocketEntity projectile = new FireworkRocketEntity(
                level,
                owner,
                player.getX(),
                player.getY() + 1d,
                player.getZ() + 6d,
                new ItemStack(Items.FIREWORK_ROCKET)
            );
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(0d, 0d, 0d);
            level.addFreshEntity(projectile);
            return new Setup(
                projectile.getId(),
                owner.getId(),
                player.damageSources().fireworks(projectile, owner).scalesWithDifficulty()
            );
        });
        validateSnapshot(context, singleplayer, setup, "firework_rocket");
    }

    private static void validateSplashPotion(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = owner(level, player);
            ThrownSplashPotion projectile = new ThrownSplashPotion(
                level,
                owner,
                new ItemStack(Items.SPLASH_POTION)
            );
            projectile.setPos(player.getX(), player.getY() + 1d, player.getZ() + 6d);
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(0d, 0d, 0d);
            level.addFreshEntity(projectile);
            return new Setup(
                projectile.getId(),
                owner.getId(),
                player.damageSources().indirectMagic(projectile, owner).scalesWithDifficulty()
            );
        });
        validateSnapshot(context, singleplayer, setup, "splash_potion");
    }

    private static void validateSnapshot(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Setup setup,
        String id
    ) {
        try {
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(setup.projectileId()) != null);
            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client unavailable for " + id + " owner scaling validation");
                }
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                WorldSnapshot.EntitySnapshot projectile = world.entities().stream()
                    .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("live " + id + " missing from production snapshot"));
                boolean predicted = Boolean.parseBoolean(
                    projectile.properties().getOrDefault("scales_with_difficulty", "false")
                );
                if (predicted != setup.serverScales()) {
                    throw new AssertionError(
                        id + " owner difficulty metadata server=" + setup.serverScales() + " snapshot=" + predicted
                    );
                }
            });
        } finally {
            discard(singleplayer, setup.projectileId());
            discard(singleplayer, setup.ownerId());
            context.waitTick();
        }
    }

    private static Creeper owner(ServerLevel level, ServerPlayer player) {
        Creeper owner = new Creeper(EntityType.CREEPER, level);
        owner.setNoAi(true);
        owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
        level.addFreshEntity(owner);
        return owner;
    }

    private static void prepare(Entity projectile, ServerPlayer player, Creeper owner) {
        if (projectile instanceof net.minecraft.world.entity.projectile.Projectile owned) {
            owned.setOwner(owner);
        }
        projectile.setPos(player.getX(), player.getY() + 1d, player.getZ() + 6d);
        projectile.setNoGravity(true);
        projectile.setDeltaMovement(0d, 0d, 0d);
    }

    private static void discard(TestSingleplayerContext singleplayer, int entityId) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Entity entity = player.level().getEntity(entityId);
            if (entity != null) entity.discard();
        });
    }

    private record Setup(int projectileId, int ownerId, boolean serverScales) {
    }
}
