package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.threat.ProjectilePredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.phys.Vec3;

final class HurtingProjectileSourceValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final float EPSILON = 0.0001f;

    private HurtingProjectileSourceValidationScenarios() {
    }

    static void validateMobOwnedSourceMetadata(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validateFireball(context, singleplayer, false);
        validateFireball(context, singleplayer, true);
        validateWitherSkull(context, singleplayer);
    }

    private static void validateFireball(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        boolean small
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = owner(level, player);
            AbstractHurtingProjectile projectile = small
                ? new SmallFireball(EntityType.SMALL_FIREBALL, level)
                : new LargeFireball(EntityType.FIREBALL, level);
            projectile.setOwner(owner);
            place(projectile, player);
            level.addFreshEntity(projectile);
            return new Setup(projectile.getId(), owner.getId());
        });

        try {
            ThreatEvent event = captureDirect(context, setup.projectileId(), small ? "small_fireball" : "large_fireball");
            if (!"minecraft:fireball".equals(event.damage().sourceKey())) {
                throw new AssertionError("mob fireball source key mismatch: " + event.damage().sourceKey());
            }
            if (!event.damage().has(DamageFlag.IS_PROJECTILE) || !event.damage().has(DamageFlag.IS_FIRE)) {
                throw new AssertionError("mob fireball must preserve vanilla projectile+fire tags: " + event.damage().flags());
            }
            if (event.damage().has(DamageFlag.BYPASSES_ARMOR) || event.damage().has(DamageFlag.BYPASSES_SHIELD)) {
                throw new AssertionError("mob fireball unexpectedly bypassed armor/shield: " + event.damage().flags());
            }
        } finally {
            discard(singleplayer, setup.projectileId());
            discard(singleplayer, setup.ownerId());
            context.waitTick();
        }
    }

    private static void validateWitherSkull(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = owner(level, player);
            WitherSkull projectile = new WitherSkull(EntityType.WITHER_SKULL, level);
            projectile.setOwner(owner);
            place(projectile, player);
            level.addFreshEntity(projectile);
            return new Setup(projectile.getId(), owner.getId());
        });

        try {
            ThreatEvent event = captureDirect(context, setup.projectileId(), "wither_skull");
            if (!"minecraft:wither_skull".equals(event.damage().sourceKey())) {
                throw new AssertionError("mob wither-skull source key mismatch: " + event.damage().sourceKey());
            }
            if (Math.abs(event.damage().rawDamage().min() - 8f) > EPSILON
                || Math.abs(event.damage().rawDamage().max() - 8f) > EPSILON) {
                throw new AssertionError("mob wither-skull direct damage must be exact vanilla 8: " + event.damage().rawDamage());
            }
            if (!event.damage().has(DamageFlag.IS_PROJECTILE)
                || event.damage().has(DamageFlag.BYPASSES_ARMOR)
                || event.damage().has(DamageFlag.BYPASSES_SHIELD)) {
                throw new AssertionError("mob wither-skull direct tags mismatch: " + event.damage().flags());
            }
        } finally {
            discard(singleplayer, setup.projectileId());
            discard(singleplayer, setup.ownerId());
            context.waitTick();
        }
    }

    private static ThreatEvent captureDirect(ClientGameTestContext context, int projectileId, String id) {
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(projectileId) != null);
        return context.computeOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client player/level unavailable for " + id + " source validation");
            }
            PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
            WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
            WorldSnapshot.EntitySnapshot projectile = world.entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(projectileId)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("live " + id + " missing from production snapshot"));
            if (!projectile.properties().containsKey("source_key")) {
                throw new AssertionError("live " + id + " snapshot omitted source_key");
            }
            PredictionContext predictionContext = new PredictionContext(
                player,
                world,
                new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                LIMITS
            );
            return new ProjectilePredictor().predict(predictionContext).stream()
                .filter(event -> event.id().equals("projectile:" + projectileId + ":direct"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("live " + id + " produced no direct threat"));
        });
    }

    private static Creeper owner(ServerLevel level, ServerPlayer player) {
        Creeper owner = new Creeper(EntityType.CREEPER, level);
        owner.setNoAi(true);
        owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
        level.addFreshEntity(owner);
        return owner;
    }

    private static void place(Entity projectile, ServerPlayer player) {
        projectile.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
        projectile.setDeltaMovement(0d, 0d, -1.5d);
    }

    private static void discard(TestSingleplayerContext singleplayer, int entityId) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Entity entity = player.level().getEntity(entityId);
            if (entity != null) entity.discard();
        });
    }

    private record Setup(int projectileId, int ownerId) {
    }
}
