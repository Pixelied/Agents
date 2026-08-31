package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ProjectilePredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Exact-runtime Task 10 matrix. Each case captures a real client-visible projectile once, then
 * deliberately keeps that remote geometry stale while the integrated server advances by a number
 * of ticks that is inside the supplied RTT/jitter observation-age envelope. Prediction is performed
 * only after that delay, so the asserted impact window is relative to server-now rather than the
 * original observed frame.
 */
final class DelayedProjectileValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final float INITIAL_HEALTH = 20f;
    private static final int MAX_DAMAGE_WAIT_TICKS = 48;

    private static final List<LatencyProfile> ARROW_MATRIX = List.of(
        new LatencyProfile("rtt0_j0", 0d, 0d, 0),
        new LatencyProfile("rtt50_j0", 50d, 0d, 1),
        new LatencyProfile("rtt100_j10", 100d, 10d, 1),
        new LatencyProfile("rtt150_j20", 150d, 20d, 2),
        new LatencyProfile("rtt200_j30", 200d, 30d, 2),
        new LatencyProfile("rtt250_j50", 250d, 50d, 3)
    );

    private DelayedProjectileValidationScenarios() {
    }

    static void validateLatencyMatrix(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (LatencyProfile profile : ARROW_MATRIX) {
            validateCase(context, singleplayer, ProjectileKind.ARROW, profile);
        }

        LatencyProfile highLatency = new LatencyProfile("rtt250_j50", 250d, 50d, 3);
        validateCase(context, singleplayer, ProjectileKind.TRIDENT, highLatency);
        validateCase(
            context,
            singleplayer,
            ProjectileKind.SPLASH_HARMING,
            new LatencyProfile("rtt200_j30", 200d, 30d, 2)
        );
        validateCase(context, singleplayer, ProjectileKind.FIREWORK, highLatency);
        validateCase(context, singleplayer, ProjectileKind.LINGERING_HARMING_CLOUD, highLatency);
    }

    private static void validateCase(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        ProjectileKind kind,
        LatencyProfile profile
    ) {
        Setup setup = spawn(singleplayer, kind);
        String id = "delayed_" + kind.id() + "_" + profile.id();
        try {
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(setup.projectileId()) != null);

            StaleObservation stale = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for " + id);
                }
                TimingSnapshot timing = new TimingSnapshot(
                    0L,
                    profile.rttMs(),
                    profile.jitterMs(),
                    new TickWindow(0L, 0L)
                );
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(
                    minecraft.level,
                    minecraft.player,
                    LIMITS,
                    timing
                );
                WorldSnapshot.EntitySnapshot projectile = world.entities().stream()
                    .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("stale projectile missing from snapshot for " + id));

                TickWindow age = timing.observationAgeWindow();
                long snapshotMin = parseNonNegative(projectile.properties().get("observation_age_min_ticks"), id);
                long snapshotMax = parseNonNegative(projectile.properties().get("observation_age_max_ticks"), id);
                if (snapshotMin != age.earliest() || snapshotMax != age.latest()) {
                    throw new AssertionError(
                        id + " observation-age metadata mismatch timing=" + age
                            + " snapshot=[" + snapshotMin + "," + snapshotMax + "]"
                    );
                }
                if (profile.delayTicks() < age.earliest() || profile.delayTicks() > age.latest()) {
                    throw new AssertionError(
                        id + " fixture delay=" + profile.delayTicks() + " outside timing age=" + age
                    );
                }
                return new StaleObservation(world, timing);
            });

            for (int tick = 0; tick < profile.delayTicks(); tick++) {
                context.waitTick();
            }
            assertStillPending(singleplayer, setup.projectileId(), id);

            ThreatEvent predicted = context.computeOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client player unavailable for " + id);
                PlayerSnapshot freshPlayer = new MinecraftSnapshotFactory().capture(minecraft.player);
                PredictionContext predictionContext = new PredictionContext(
                    freshPlayer,
                    stale.world(),
                    stale.timing(),
                    LIMITS
                );
                return new ProjectilePredictor().predict(predictionContext).stream()
                    .filter(event -> event.id().equals(kind.eventId(setup.projectileId())))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no delayed projectile prediction for " + id));
            });

            if (!kind.sourceKey().equals(predicted.damage().sourceKey())) {
                throw new AssertionError(
                    id + " source expected=" + kind.sourceKey() + " actual=" + predicted.damage().sourceKey()
                );
            }

            int actualImpactTicks = waitForDamage(context, singleplayer, id);
            if (actualImpactTicks < predicted.impact().earliest() || actualImpactTicks > predicted.impact().latest()) {
                throw new AssertionError(
                    id + " impact predicted=" + predicted.impact() + " actualTick=" + actualImpactTicks
                        + " rttMs=" + profile.rttMs() + " jitterMs=" + profile.jitterMs()
                        + " staleDelayTicks=" + profile.delayTicks()
                );
            }
        } finally {
            cleanup(context, singleplayer, setup);
        }
    }

    private static Setup spawn(TestSingleplayerContext singleplayer, ProjectileKind kind) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, INITIAL_HEALTH);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            return switch (kind) {
                case ARROW -> {
                    Arrow arrow = new Arrow(
                        level,
                        player.getX(),
                        player.getEyeY() - 0.15d,
                        player.getZ() + 18d,
                        new ItemStack(Items.ARROW),
                        null
                    );
                    arrow.setNoGravity(true);
                    arrow.setDeltaMovement(0d, 0d, -1.5d);
                    level.addFreshEntity(arrow);
                    yield new Setup(arrow.getId(), -1);
                }
                case TRIDENT -> {
                    ThrownTrident trident = new ThrownTrident(
                        level,
                        player.getX(),
                        player.getEyeY() - 0.15d,
                        player.getZ() + 18d,
                        new ItemStack(Items.TRIDENT)
                    );
                    trident.setNoGravity(true);
                    trident.setDeltaMovement(0d, 0d, -1.5d);
                    level.addFreshEntity(trident);
                    yield new Setup(trident.getId(), -1);
                }
                case SPLASH_HARMING -> spawnPotion(level, player, false);
                case LINGERING_HARMING_CLOUD -> spawnPotion(level, player, true);
                case FIREWORK -> {
                    ItemStack rocketStack = new ItemStack(Items.FIREWORK_ROCKET);
                    rocketStack.set(
                        DataComponents.FIREWORKS,
                        new Fireworks(0, List.of(FireworkExplosion.DEFAULT))
                    );
                    FireworkRocketEntity rocket = new FireworkRocketEntity(
                        level,
                        rocketStack,
                        player.getX(),
                        player.getEyeY() - 0.15d,
                        player.getZ() + 3d,
                        true
                    );
                    rocket.setDeltaMovement(Vec3.ZERO);
                    level.addFreshEntity(rocket);
                    yield new Setup(rocket.getId(), -1);
                }
            };
        });
    }

    private static Setup spawnPotion(ServerLevel level, ServerPlayer player, boolean lingering) {
        Creeper owner = new Creeper(EntityType.CREEPER, level);
        owner.setNoAi(true);
        owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
        level.addFreshEntity(owner);

        ItemStack stack = new ItemStack(lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION);
        stack.set(
            DataComponents.POTION_CONTENTS,
            PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1))
        );
        Entity projectile;
        if (lingering) {
            ThrownLingeringPotion potion = new ThrownLingeringPotion(level, owner, stack);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 18d);
            potion.setNoGravity(true);
            potion.setDeltaMovement(0d, 0d, -1.5d);
            projectile = potion;
        } else {
            ThrownSplashPotion potion = new ThrownSplashPotion(level, owner, stack);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 18d);
            potion.setNoGravity(true);
            potion.setDeltaMovement(0d, 0d, -1.5d);
            projectile = potion;
        }
        level.addFreshEntity(projectile);
        return new Setup(projectile.getId(), owner.getId());
    }

    private static void assertStillPending(
        TestSingleplayerContext singleplayer,
        int projectileId,
        String id
    ) {
        PendingState state = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            return new PendingState(
                player.getHealth(),
                ((ServerLevel) player.level()).getEntity(projectileId) != null
            );
        });
        if (state.health() < INITIAL_HEALTH) {
            throw new AssertionError(id + " fixture impacted during the deliberate stale-observation delay");
        }
        if (!state.projectilePresent()) {
            throw new AssertionError(id + " projectile disappeared during the deliberate stale-observation delay");
        }
    }

    private static int waitForDamage(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        String id
    ) {
        for (int tick = 1; tick <= MAX_DAMAGE_WAIT_TICKS; tick++) {
            context.waitTick();
            float health = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            if (health < INITIAL_HEALTH) return tick;
        }
        throw new AssertionError(id + " did not damage the player within " + MAX_DAMAGE_WAIT_TICKS + " ticks");
    }

    private static void cleanup(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Setup setup
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Entity projectile = level.getEntity(setup.projectileId());
            if (projectile != null) projectile.discard();
            if (setup.ownerId() >= 0) {
                Entity owner = level.getEntity(setup.ownerId());
                if (owner != null) owner.discard();
            }
            for (AreaEffectCloud cloud : level.getEntitiesOfClass(
                AreaEffectCloud.class,
                player.getBoundingBox().inflate(32d)
            )) {
                cloud.discard();
            }
            SurvivalValidationClientGameTest.reset(player, INITIAL_HEALTH);
            player.setDeltaMovement(Vec3.ZERO);
        });
        context.waitTick();
    }

    private static long parseNonNegative(String value, String id) {
        if (value == null) throw new AssertionError(id + " missing observation-age metadata");
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0L) throw new NumberFormatException("negative");
            return parsed;
        } catch (NumberFormatException error) {
            throw new AssertionError(id + " malformed observation-age metadata=" + value, error);
        }
    }

    private enum ProjectileKind {
        ARROW("arrow", "minecraft:arrow", ":direct"),
        TRIDENT("trident", "minecraft:trident", ":direct"),
        SPLASH_HARMING("splash_harming", "minecraft:indirect_magic", ":splash_magic"),
        FIREWORK("firework", "minecraft:fireworks", ":firework"),
        LINGERING_HARMING_CLOUD("lingering_cloud", "minecraft:indirect_magic", ":lingering_cloud:0");

        private final String id;
        private final String sourceKey;
        private final String eventSuffix;

        ProjectileKind(String id, String sourceKey, String eventSuffix) {
            this.id = id;
            this.sourceKey = sourceKey;
            this.eventSuffix = eventSuffix;
        }

        String id() {
            return id;
        }

        String sourceKey() {
            return sourceKey;
        }

        String eventId(int projectileId) {
            return "projectile:" + projectileId + eventSuffix;
        }
    }

    private record LatencyProfile(String id, double rttMs, double jitterMs, int delayTicks) {
        LatencyProfile {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be nonblank");
            if (!Double.isFinite(rttMs) || rttMs < 0d) throw new IllegalArgumentException("rttMs must be non-negative");
            if (!Double.isFinite(jitterMs) || jitterMs < 0d) throw new IllegalArgumentException("jitterMs must be non-negative");
            if (delayTicks < 0) throw new IllegalArgumentException("delayTicks must be non-negative");
        }
    }

    private record Setup(int projectileId, int ownerId) {
    }

    private record StaleObservation(WorldSnapshot world, TimingSnapshot timing) {
    }

    private record PendingState(float health, boolean projectilePresent) {
    }
}
