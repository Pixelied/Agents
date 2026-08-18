package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
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
            Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, new ItemStack(Items.ARROW), null);
            arrow.setOwner(owner);
            arrow.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(arrow);
            return new ProjectileSetup(arrow.getId(), owner.getId());
        });

        ValidationResult result;
        try {
            result = validateLiveProjectile(
                context,
                singleplayer,
                setup.projectileId(),
                "minecraft:arrow",
                "mob_arrow_hard_scaling"
            );
        } finally {
            discard(singleplayer, setup.ownerId());
            singleplayer.getServer().runOnServer(server -> server.setDifficulty(Difficulty.NORMAL, true));
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getDifficulty() == Difficulty.NORMAL);
        }
        return result;
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
}
