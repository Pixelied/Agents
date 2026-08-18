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

            ThrownSplashPotion potion = splashHarming(level, owner);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            potion.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(potion);
            return new Setup(
                potion.getId(),
                owner.getId(),
                player.position().toString(),
                player.getBoundingBox().toString(),
                potion.position().toString(),
                String.valueOf(potion.getItem().get(DataComponents.POTION_CONTENTS))
            );
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(setup.projectileId()) != null);
            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent predicted = frame.timeline().events().stream()
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":"))
                .findFirst()
                .orElse(null);

            String snapshot = snapshot(frame, setup.projectileId());
            float actualHealth = awaitDamage(context, singleplayer, setup.projectileId(), snapshot, setup);

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
            cleanup(singleplayer, setup.projectileId(), setup.ownerId());
            context.waitTick();
        }
    }

    static void validateSplashHarmingWallFalloffMatchesVanilla(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        WallSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            BlockPos wallBase = player.blockPosition().offset(0, 0, 2);
            level.setBlockAndUpdate(wallBase, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(wallBase.above(), Blocks.STONE.defaultBlockState());

            Creeper owner = new Creeper(EntityType.CREEPER, level);
            owner.setNoAi(true);
            owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
            level.addFreshEntity(owner);

            ThrownSplashPotion potion = splashHarming(level, owner);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 5d);
            potion.setDeltaMovement(0d, 0d, -0.6d);
            level.addFreshEntity(potion);
            return new WallSetup(
                potion.getId(),
                owner.getId(),
                wallBase,
                player.position().toString(),
                player.getBoundingBox().toString(),
                potion.position().toString(),
                potion.getDeltaMovement().toString()
            );
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) != null
                && minecraft.level.getBlockState(setup.wallBase()).is(Blocks.STONE)
                && minecraft.level.getBlockState(setup.wallBase().above()).is(Blocks.STONE));

            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent predicted = frame.timeline().events().stream()
                .filter(event -> event.id().equals("projectile:" + setup.projectileId() + ":splash_magic"))
                .findFirst()
                .orElse(null);
            String snapshot = wallSnapshot(frame, setup.projectileId(), setup.wallBase());

            DamageObservation damage = awaitDamageDetailed(context, singleplayer, setup.projectileId(), snapshot, setup);
            float actualHealth = damage.health();
            if (!(actualHealth > 8f && actualHealth < 20f)) {
                throw new AssertionError(
                    "wall-splash fixture did not produce reduced Harming damage; actualHealth=" + actualHealth
                        + " " + snapshot + " setup=" + setup + " damage=" + damage
                );
            }
            if (predicted == null) {
                throw new AssertionError(
                    "wall-splash Harming caused vanilla damage but production emitted no splash_magic threat; "
                        + "actualHealth=" + actualHealth + " " + snapshot + " setup=" + setup + " damage=" + damage
                        + " projectileEvents=" + projectileEvents(frame, setup.projectileId())
                );
            }

            float actualDamage = 20f - actualHealth;
            float predictedMin = predicted.damage().rawDamage().min();
            float predictedMax = predicted.damage().rawDamage().max();
            if (actualDamage < predictedMin - EPSILON || actualDamage > predictedMax + EPSILON) {
                throw new AssertionError(
                    "wall-splash Harming damage fell outside prediction bounds; predictedRange="
                        + predicted.damage().rawDamage() + " actualDamage=" + actualDamage
                        + " event=" + predicted + " " + snapshot + " setup=" + setup + " damage=" + damage
                );
            }

            float conservativeHealth = new DamageSimulator().simulate(frame.context().player(), predicted.damage()).after().health();
            if (conservativeHealth > actualHealth + EPSILON) {
                throw new AssertionError(
                    "wall-splash Harming upper bound was safer than vanilla; predicted=" + conservativeHealth
                        + " actual=" + actualHealth + " event=" + predicted + " " + snapshot
                        + " setup=" + setup + " damage=" + damage
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
            });
            context.waitTick();
        }
    }

    private static ThrownSplashPotion splashHarming(ServerLevel level, Creeper owner) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(
            DataComponents.POTION_CONTENTS,
            PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1))
        );
        return new ThrownSplashPotion(level, owner, stack);
    }

    private static String snapshot(SurvivalEngine.EngineFrame frame, int projectileId) {
        return frame.context().world().entities().stream()
            .filter(entity -> entity.id().equals(Integer.toString(projectileId)))
            .map(entity -> "type=" + entity.typeKey()
                + " position=" + entity.position()
                + " velocity=" + entity.velocity()
                + " box=" + entity.boundingBox()
                + " properties=" + entity.properties()
                + " playerPosition=" + frame.context().player().position()
                + " playerVelocity=" + frame.context().player().velocity())
            .findFirst()
            .orElse("<projectile missing from snapshot>");
    }

    private static String wallSnapshot(SurvivalEngine.EngineFrame frame, int projectileId, BlockPos wallBase) {
        List<String> wallBlocks = frame.context().world().blocks().stream()
            .filter(block -> block.blockId().equals("minecraft:stone"))
            .filter(block -> matches(block.position(), wallBase) || matches(block.position(), wallBase.above()))
            .map(block -> block.position() + " properties=" + block.properties())
            .toList();
        return snapshot(frame, projectileId) + " wallBase=" + wallBase + " wallSnapshots=" + wallBlocks;
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

    private static float awaitDamage(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int projectileId,
        String snapshot,
        Object setup
    ) {
        return awaitDamageDetailed(context, singleplayer, projectileId, snapshot, setup).health();
    }

    private static DamageObservation awaitDamageDetailed(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int projectileId,
        String snapshot,
        Object setup
    ) {
        Observation lastPresent = null;
        Observation current = null;
        int disappearedAt = -1;
        for (int tick = 1; tick <= 30; tick++) {
            context.waitTick();
            current = singleplayer.getServer().computeOnServer(server -> observe(server, projectileId));
            if (current.present()) lastPresent = current;
            if (!current.present() && disappearedAt < 0) disappearedAt = tick;
            if (current.health() < 20f) {
                return new DamageObservation(current.health(), tick, disappearedAt, lastPresent, current);
            }
        }
        throw new AssertionError(
            "splash Harming II fixture produced no server damage within 30 ticks; "
                + snapshot + " setup=" + setup + " disappearedAt=" + disappearedAt
                + " lastPresent=" + lastPresent + " current=" + current
        );
    }

    private static void cleanup(TestSingleplayerContext singleplayer, int projectileId, int ownerId) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Entity projectile = player.level().getEntity(projectileId);
            if (projectile != null) projectile.discard();
            Entity owner = player.level().getEntity(ownerId);
            if (owner != null) owner.discard();
            SurvivalValidationClientGameTest.reset(player, 20f);
        });
    }

    private static Observation observe(net.minecraft.server.MinecraftServer server, int projectileId) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        Entity entity = player.level().getEntity(projectileId);
        if (entity instanceof ThrownSplashPotion potion) {
            return new Observation(
                player.getHealth(),
                true,
                potion.position().toString(),
                potion.getDeltaMovement().toString(),
                String.valueOf(potion.getItem().get(DataComponents.POTION_CONTENTS))
            );
        }
        return new Observation(player.getHealth(), false, "<gone>", "<gone>", "<gone>");
    }

    private record Setup(
        int projectileId,
        int ownerId,
        String playerPosition,
        String playerBox,
        String potionPosition,
        String potionContents
    ) {
    }

    private record WallSetup(
        int projectileId,
        int ownerId,
        BlockPos wallBase,
        String serverPlayerPosition,
        String serverPlayerBox,
        String serverPotionPosition,
        String serverPotionVelocity
    ) {
    }

    private record Observation(
        float health,
        boolean present,
        String position,
        String velocity,
        String potionContents
    ) {
    }

    private record DamageObservation(
        float health,
        int damageTick,
        int disappearedAt,
        Observation lastPresent,
        Observation atDamage
    ) {
    }
}
