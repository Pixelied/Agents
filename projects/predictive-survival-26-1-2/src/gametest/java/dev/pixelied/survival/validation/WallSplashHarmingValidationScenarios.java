package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.damage.DamageSimulator;
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

import java.util.List;

final class WallSplashHarmingValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double ANCHOR_TOLERANCE_SQUARED = 0.01d;

    private WallSplashHarmingValidationScenarios() {
    }

    static void validateWallFalloffMatchesVanilla(
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
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1))
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

            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent predicted = frame.timeline().events().stream()
                .filter(event -> event.id().equals("projectile:" + setup.projectileId() + ":splash_magic"))
                .findFirst()
                .orElse(null);
            String snapshot = diagnostic(frame, setup);

            DamageObservation damage = awaitDamage(context, singleplayer, setup);
            float actualHealth = damage.health();
            if (!(actualHealth > 8f && actualHealth < 20f)) {
                throw new AssertionError(
                    "anchored wall-splash fixture did not produce reduced Harming damage; actualHealth="
                        + actualHealth + " " + snapshot + " damage=" + damage
                );
            }
            if (predicted == null) {
                throw new AssertionError(
                    "anchored wall-splash Harming caused vanilla damage but production emitted no splash_magic threat; "
                        + "actualHealth=" + actualHealth + " " + snapshot + " damage=" + damage
                        + " projectileEvents=" + projectileEvents(frame, setup.projectileId())
                );
            }

            float actualDamage = 20f - actualHealth;
            float predictedMin = predicted.damage().rawDamage().min();
            float predictedMax = predicted.damage().rawDamage().max();
            if (actualDamage < predictedMin - EPSILON || actualDamage > predictedMax + EPSILON) {
                throw new AssertionError(
                    "anchored wall-splash Harming damage fell outside prediction bounds; predictedRange="
                        + predicted.damage().rawDamage() + " actualDamage=" + actualDamage
                        + " event=" + predicted + " " + snapshot + " damage=" + damage
                );
            }

            float conservativeHealth = new DamageSimulator().simulate(frame.context().player(), predicted.damage()).after().health();
            if (conservativeHealth > actualHealth + EPSILON) {
                throw new AssertionError(
                    "anchored wall-splash Harming upper bound was safer than vanilla; predicted=" + conservativeHealth
                        + " actual=" + actualHealth + " event=" + predicted + " " + snapshot + " damage=" + damage
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
                player.setNoGravity(false);
                player.setPos(setup.playerAnchor().x, setup.playerAnchor().y, setup.playerAnchor().z);
                player.setDeltaMovement(Vec3.ZERO);
            });
            anchorClient(context, setup.playerAnchor());
            context.waitTick();
        }
    }

    private static DamageObservation awaitDamage(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Setup setup
    ) {
        Observation last = null;
        int disappearedAt = -1;
        for (int tick = 1; tick <= 30; tick++) {
            anchorBoth(context, singleplayer, setup.playerAnchor());
            context.waitTick();
            last = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));
            if (!last.projectilePresent() && disappearedAt < 0) disappearedAt = tick;
            if (last.health() < 20f) {
                return new DamageObservation(last.health(), tick, disappearedAt, last);
            }
        }
        throw new AssertionError(
            "anchored wall-splash fixture produced no server damage within 30 ticks; setup=" + setup
                + " disappearedAt=" + disappearedAt + " last=" + last
        );
    }

    private static Observation observe(net.minecraft.server.MinecraftServer server, int projectileId) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        var source = player.getLastDamageSource();
        return new Observation(
            player.getHealth(),
            player.level().getEntity(projectileId) != null,
            player.position(),
            player.getDeltaMovement(),
            String.valueOf(source),
            source == null ? "<none>" : String.valueOf(source.getSourcePosition())
        );
    }

    private static void anchorBoth(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Vec3 anchor
    ) {
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

    private static String diagnostic(SurvivalEngine.EngineFrame frame, Setup setup) {
        String projectile = frame.context().world().entities().stream()
            .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
            .map(entity -> "type=" + entity.typeKey()
                + " position=" + entity.position()
                + " velocity=" + entity.velocity()
                + " properties=" + entity.properties())
            .findFirst()
            .orElse("<projectile missing from snapshot>");
        List<String> wallBlocks = frame.context().world().blocks().stream()
            .filter(block -> block.blockId().equals("minecraft:stone"))
            .filter(block -> matches(block.position(), setup.wallBase())
                || matches(block.position(), setup.wallBase().above()))
            .map(block -> block.position() + " properties=" + block.properties())
            .toList();
        return projectile
            + " playerPosition=" + frame.context().player().position()
            + " playerVelocity=" + frame.context().player().velocity()
            + " anchor=" + setup.playerAnchor()
            + " wallBase=" + setup.wallBase()
            + " wallSnapshots=" + wallBlocks;
    }

    private static boolean matches(dev.pixelied.survival.core.Vec3Snapshot center, BlockPos pos) {
        return Math.floor(center.x()) == pos.getX()
            && Math.floor(center.y()) == pos.getY()
            && Math.floor(center.z()) == pos.getZ();
    }

    private static List<String> projectileEvents(SurvivalEngine.EngineFrame frame, int projectileId) {
        String prefix = "projectile:" + projectileId + ":";
        return frame.timeline().events().stream()
            .filter(event -> event.id().startsWith(prefix))
            .map(Object::toString)
            .toList();
    }

    private record Setup(int projectileId, int ownerId, BlockPos wallBase, Vec3 playerAnchor) {
    }

    private record Observation(
        float health,
        boolean projectilePresent,
        Vec3 playerPosition,
        Vec3 playerVelocity,
        String lastDamageSource,
        String damageSourcePosition
    ) {
    }

    private record DamageObservation(
        float health,
        int damageTick,
        int disappearedAt,
        Observation atDamage
    ) {
    }
}
