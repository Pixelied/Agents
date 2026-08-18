package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.threat.ProjectilePredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class ProjectileValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private ProjectileValidationScenarios() {
    }

    static List<ValidationResult> runtimeSlice(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        List<ValidationResult> results = new ArrayList<>();
        results.add(validateArrow(context, singleplayer));
        results.add(validateTrident(context, singleplayer));
        results.add(validateMobOwnedArrowHardScaling(context, singleplayer));
        results.add(validateMobOwnedTridentHardScaling(context, singleplayer));
        validateMobOwnedLargeFireballDifficultyMetadata(context, singleplayer);
        validateMobOwnedSmallFireballDifficultyMetadata(context, singleplayer);
        validateMobOwnedWitherSkullDifficultyMetadata(context, singleplayer);
        return List.copyOf(results);
    }

    private static ValidationResult validateArrow(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int entityId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 spawn = new Vec3(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, new ItemStack(Items.ARROW), null);
            arrow.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(arrow);
            return arrow.getId();
        });

        return validateLiveProjectile(context, singleplayer, entityId, "minecraft:arrow", "arrow_flight");
    }

    private static ValidationResult validateTrident(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int entityId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 spawn = new Vec3(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            ThrownTrident trident = new ThrownTrident(
                level,
                spawn.x,
                spawn.y,
                spawn.z,
                new ItemStack(Items.TRIDENT)
            );
            trident.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(trident);
            return trident.getId();
        });

        return validateLiveProjectile(context, singleplayer, entityId, "minecraft:trident", "trident_flight");
    }

    private static ValidationResult validateMobOwnedArrowHardScaling(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        return validateMobOwnedProjectileHardScaling(context, singleplayer, false);
    }

    private static ValidationResult validateMobOwnedTridentHardScaling(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        return validateMobOwnedProjectileHardScaling(context, singleplayer, true);
    }

    private static ValidationResult validateMobOwnedProjectileHardScaling(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        boolean trident
    ) {
        singleplayer.getServer().runOnServer(server -> server.setDifficulty(Difficulty.HARD, true));
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getDifficulty() == Difficulty.HARD);

        ProjectileSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            Creeper owner = new Creeper(EntityType.CREEPER, level);
            owner.setNoAi(true);
            owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
            level.addFreshEntity(owner);

            Vec3 spawn = new Vec3(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            Entity projectile;
            if (trident) {
                ThrownTrident thrown = new ThrownTrident(
                    level,
                    spawn.x,
                    spawn.y,
                    spawn.z,
                    new ItemStack(Items.TRIDENT)
                );
                thrown.setOwner(owner);
                thrown.setDeltaMovement(0d, 0d, -1.5d);
                projectile = thrown;
            } else {
                Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, new ItemStack(Items.ARROW), null);
                arrow.setOwner(owner);
                arrow.setDeltaMovement(0d, 0d, -1.5d);
                projectile = arrow;
            }
            level.addFreshEntity(projectile);
            return new ProjectileSetup(projectile.getId(), owner.getId());
        });

        String sourceKey = trident ? "minecraft:trident" : "minecraft:arrow";
        String id = trident ? "mob_trident_hard_scaling" : "mob_arrow_hard_scaling";
        ValidationResult result;
        try {
            result = validateLiveProjectile(context, singleplayer, setup.projectileId(), sourceKey, id);
        } finally {
            discard(singleplayer, setup.ownerId());
            singleplayer.getServer().runOnServer(server -> server.setDifficulty(Difficulty.NORMAL, true));
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getDifficulty() == Difficulty.NORMAL);
        }
        return result;
    }

    private static void validateMobOwnedLargeFireballDifficultyMetadata(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        HurtingProjectileSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = createOwner(level, player);

            LargeFireball projectile = new LargeFireball(EntityType.FIREBALL, level);
            projectile.setOwner(owner);
            placeProjectile(projectile, player);
            level.addFreshEntity(projectile);
            boolean serverScales = player.damageSources().fireball(projectile, owner).scalesWithDifficulty();
            return new HurtingProjectileSetup(projectile.getId(), owner.getId(), serverScales);
        });

        validateHurtingProjectileDifficultyMetadata(context, singleplayer, setup, "large_fireball");
    }

    private static void validateMobOwnedSmallFireballDifficultyMetadata(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        HurtingProjectileSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = createOwner(level, player);

            SmallFireball projectile = new SmallFireball(EntityType.SMALL_FIREBALL, level);
            projectile.setOwner(owner);
            placeProjectile(projectile, player);
            level.addFreshEntity(projectile);
            boolean serverScales = player.damageSources().fireball(projectile, owner).scalesWithDifficulty();
            return new HurtingProjectileSetup(projectile.getId(), owner.getId(), serverScales);
        });

        validateHurtingProjectileDifficultyMetadata(context, singleplayer, setup, "small_fireball");
    }

    private static void validateMobOwnedWitherSkullDifficultyMetadata(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        HurtingProjectileSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Creeper owner = createOwner(level, player);

            WitherSkull projectile = new WitherSkull(EntityType.WITHER_SKULL, level);
            projectile.setOwner(owner);
            placeProjectile(projectile, player);
            level.addFreshEntity(projectile);
            boolean serverScales = player.damageSources().witherSkull(projectile, owner).scalesWithDifficulty();
            return new HurtingProjectileSetup(projectile.getId(), owner.getId(), serverScales);
        });

        validateHurtingProjectileDifficultyMetadata(context, singleplayer, setup, "wither_skull");
    }

    private static Creeper createOwner(ServerLevel level, ServerPlayer player) {
        Creeper owner = new Creeper(EntityType.CREEPER, level);
        owner.setNoAi(true);
        owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
        level.addFreshEntity(owner);
        return owner;
    }

    private static void placeProjectile(Entity projectile, ServerPlayer player) {
        projectile.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
        projectile.setDeltaMovement(0d, 0d, -1.5d);
    }

    private static void validateHurtingProjectileDifficultyMetadata(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        HurtingProjectileSetup setup,
        String id
    ) {
        try {
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(setup.projectileId()) != null);
            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for mob " + id + " difficulty validation");
                }
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                WorldSnapshot.EntitySnapshot projectile = world.entities().stream()
                    .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("live mob-owned " + id + " missing from world snapshot"));
                boolean snapshotScales = Boolean.parseBoolean(
                    projectile.properties().getOrDefault("scales_with_difficulty", "false")
                );
                if (snapshotScales != setup.serverScales()) {
                    throw new AssertionError(
                        "mob_" + id + "_difficulty_metadata server=" + setup.serverScales()
                            + " snapshot=" + snapshotScales
                    );
                }

                PlayerSnapshot playerSnapshot = new MinecraftSnapshotFactory().capture(minecraft.player);
                PredictionContext predictionContext = new PredictionContext(
                    playerSnapshot,
                    world,
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                List<ThreatEvent> events = new ProjectilePredictor().predict(predictionContext).stream()
                    .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":"))
                    .toList();
                if (events.isEmpty()) throw new AssertionError("live mob-owned " + id + " produced no projectile threat");
                for (ThreatEvent event : events) {
                    if (event.damage().scalesWithDifficulty() != setup.serverScales()) {
                        throw new AssertionError(
                            "mob_" + id + "_predictor_scaling event=" + event.id()
                                + " server=" + setup.serverScales()
                                + " predicted=" + event.damage().scalesWithDifficulty()
                        );
                    }
                }
            });
        } finally {
            discard(singleplayer, setup.projectileId());
            discard(singleplayer, setup.ownerId());
            context.waitTick();
        }
    }

    private static ValidationResult validateLiveProjectile(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int entityId,
        String sourceKey,
        String id
    ) {
        context.waitTick();
        LivePrediction livePrediction = context.computeOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client player/level unavailable while capturing " + id);
            }
            Entity clientEntity = minecraft.level.getEntity(entityId);
            if (clientEntity == null) throw new AssertionError("client projectile missing for " + id);
            PlayerSnapshot playerSnapshot = new MinecraftSnapshotFactory().capture(minecraft.player);
            PredictionContext predictionContext = new PredictionContext(
                playerSnapshot,
                new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS),
                new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                LIMITS
            );
            ThreatEvent event = new ProjectilePredictor().predict(predictionContext).stream()
                .filter(candidate -> candidate.id().startsWith("projectile:" + entityId + ":"))
                .filter(candidate -> candidate.damage().sourceKey().equals(sourceKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no live projectile prediction for " + id));
            return new LivePrediction(
                playerSnapshot,
                event,
                vec(clientEntity.position()),
                vec(clientEntity.getDeltaMovement())
            );
        });
        ProjectileState serverAtPrediction = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
            if (entity == null) throw new AssertionError("server projectile missing for " + id);
            return new ProjectileState(vec(entity.position()), vec(entity.getDeltaMovement()));
        });

        int actualImpactTicks = waitForDamage(context, singleplayer, 20f, id);
        float actualHealth = singleplayer.getServer().computeOnServer(server ->
            SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
        );
        ThreatEvent prediction = livePrediction.event();
        if (actualImpactTicks < prediction.impact().earliest() || actualImpactTicks > prediction.impact().latest()) {
            throw new AssertionError(
                id + " impact predicted=" + prediction.impact() + " actualTick=" + actualImpactTicks
                    + " clientPos=" + livePrediction.projectilePosition()
                    + " serverPos=" + serverAtPrediction.position()
                    + " clientVelocity=" + livePrediction.projectileVelocity()
                    + " serverVelocity=" + serverAtPrediction.velocity()
            );
        }

        float predictedHealth = SIMULATOR.simulate(livePrediction.player(), prediction.damage()).after().health();
        discard(singleplayer, entityId);
        context.waitTick();
        return new ValidationResult(
            id,
            predictedHealth,
            actualHealth,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
        );
    }

    private static int waitForDamage(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        float initialHealth,
        String id
    ) {
        for (int tick = 1; tick <= 20; tick++) {
            context.waitTick();
            float health = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            if (health < initialHealth) return tick;
        }
        throw new AssertionError(id + " did not hit the player within 20 ticks");
    }

    private static void discard(TestSingleplayerContext singleplayer, int entityId) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
            if (entity != null) entity.discard();
        });
    }

    private static Vec3Snapshot vec(Vec3 value) {
        return new Vec3Snapshot(value.x, value.y, value.z);
    }

    private record LivePrediction(
        PlayerSnapshot player,
        ThreatEvent event,
        Vec3Snapshot projectilePosition,
        Vec3Snapshot projectileVelocity
    ) {
    }

    private record ProjectileState(Vec3Snapshot position, Vec3Snapshot velocity) {
    }

    private record ProjectileSetup(int projectileId, int ownerId) {
    }

    private record HurtingProjectileSetup(int projectileId, int ownerId, boolean serverScales) {
    }
}
