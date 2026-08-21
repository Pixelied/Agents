package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class WitherSkeletonMeleeValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private WitherSkeletonMeleeValidationScenarios() {
    }

    static void validateRealMeleeDamageFollowupAndProactiveProtection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int skeletonId = spawnFrozenWitherSkeleton(singleplayer, 20f);
        try {
            waitForTrackedMeleeThreat(context, skeletonId, 20f);

            PredictionObservation prediction = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for Wither Skeleton validation");
                }
                var frame = new MinecraftSurvivalRuntime(minecraft).capture();
                ThreatEvent direct = frame.timeline().events().stream()
                    .filter(event -> event.id().equals("melee:" + skeletonId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("real Wither Skeleton produced no direct melee threat"));
                ThreatEvent wither = frame.timeline().events().stream()
                    .filter(event -> event.requiresAcceptedEventId().filter(direct.id()::equals).isPresent())
                    .filter(event -> event.damage().sourceKey().equals("minecraft:wither"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Wither Skeleton direct hit produced no causal Wither follow-up"));
                if (wither.damage().rawDamage().max() != 1f) {
                    throw new AssertionError("Wither follow-up did not use vanilla 1 damage effect tick: " + wither.damage().rawDamage());
                }
                float predictedHealth = new DamageSimulator()
                    .simulate(frame.context().player(), direct.damage())
                    .after()
                    .health();
                return new PredictionObservation(predictedHealth, direct.damage().rawDamage().min(), direct.damage().rawDamage().max());
            });

            DirectAttackObservation actual = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                Entity entity = level.getEntity(skeletonId);
                if (!(entity instanceof WitherSkeleton skeleton)) {
                    throw new AssertionError("Wither Skeleton disappeared before direct-damage validation");
                }
                player.invulnerableTime = 0;
                player.setHealth(20f);
                boolean accepted = skeleton.doHurtTarget(level, player);
                MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
                return new DirectAttackObservation(
                    accepted,
                    player.getHealth(),
                    wither == null ? -1 : wither.getDuration()
                );
            });

            if (!actual.accepted()) throw new AssertionError("real Wither Skeleton direct attack was not accepted");
            SurvivalValidationClientGameTest.assertClose(
                "real_wither_skeleton_direct_damage",
                prediction.predictedHealth(),
                actual.health(),
                EPSILON
            );
            if (actual.witherDuration() != 200) {
                throw new AssertionError("real Wither Skeleton Wither duration expected=200 actual=" + actual.witherDuration());
            }
            if (!(prediction.rawMax() >= prediction.rawMin()) || prediction.rawMin() <= 0f) {
                throw new AssertionError("invalid reconstructed Wither Skeleton direct damage bound: " + prediction);
            }

            prepareLowHealthTotemScenario(singleplayer, skeletonId);
            waitForLowHealthScenarioSync(context, skeletonId);
            waitForServerAuthoritativeTotemSelection(context, singleplayer);

            PopObservation pop = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                Entity entity = level.getEntity(skeletonId);
                if (!(entity instanceof WitherSkeleton skeleton)) {
                    throw new AssertionError("Wither Skeleton disappeared before proactive-protection validation");
                }
                if (player.getInventory().getSelectedSlot() != 1 || !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server did not observe Totem in selected main hand before attack");
                }
                player.invulnerableTime = 0;
                boolean accepted = skeleton.doHurtTarget(level, player);
                MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
                return new PopObservation(
                    accepted,
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    wither != null
                );
            });

            if (!pop.accepted()) throw new AssertionError("low-health Wither Skeleton attack was not accepted");
            SurvivalValidationClientGameTest.assertClose("real_wither_skeleton_proactive_pop", 1f, pop.health(), EPSILON);
            if (!pop.totemConsumed()) throw new AssertionError("proactively selected Totem was not consumed by lethal Wither Skeleton hit");
            if (!pop.witherApplied()) throw new AssertionError("Wither follow-up was not applied after successful Totem-protected direct hit");
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity skeleton = player.level().getEntity(skeletonId);
                if (skeleton != null) skeleton.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            });
            context.waitTick();
        }
    }

    private static int spawnFrozenWitherSkeleton(TestSingleplayerContext singleplayer, float health) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, health);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();
            WitherSkeleton skeleton = EntityType.WITHER_SKELETON.spawn(
                level,
                player.blockPosition().offset(0, 0, 1),
                EntitySpawnReason.TRIGGERED
            );
            if (skeleton == null) throw new AssertionError("failed to spawn real Wither Skeleton");
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            skeleton.setPersistenceRequired();
            skeleton.setDeltaMovement(Vec3.ZERO);
            skeleton.setPos(player.getX(), player.getY(), player.getZ() + 1.0d);
            return skeleton.getId();
        });
    }

    private static void waitForTrackedMeleeThreat(ClientGameTestContext context, int skeletonId, float expectedHealth) {
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.level != null
            && minecraft.level.getEntity(skeletonId) instanceof WitherSkeleton
            && Math.abs(minecraft.player.getHealth() - expectedHealth) <= EPSILON);
        context.waitFor(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) return false;
            return new MinecraftSurvivalRuntime(minecraft).capture().timeline().events().stream()
                .anyMatch(event -> event.id().equals("melee:" + skeletonId));
        });
    }

    private static void prepareLowHealthTotemScenario(TestSingleplayerContext singleplayer, int skeletonId) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STONE));
            player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
            Entity entity = player.level().getEntity(skeletonId);
            if (!(entity instanceof WitherSkeleton skeleton)) {
                throw new AssertionError("Wither Skeleton disappeared while preparing low-health scenario");
            }
            skeleton.setPos(player.getX(), player.getY(), player.getZ() + 1.0d);
            skeleton.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static void waitForLowHealthScenarioSync(ClientGameTestContext context, int skeletonId) {
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.level != null
            && minecraft.level.getEntity(skeletonId) instanceof WitherSkeleton
            && Math.abs(minecraft.player.getHealth() - 4f) <= EPSILON
            && minecraft.player.getInventory().getSelectedSlot() == 0
            && minecraft.player.getInventory().getItem(0).is(Items.STONE)
            && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));
    }

    private static void waitForServerAuthoritativeTotemSelection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean selected = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getInventory().getSelectedSlot() == 1
                    && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
            });
            if (selected) return;
            context.waitTick();
        }
        throw new AssertionError("Predictive Survival did not make Totem server-authoritative before preventable Wither Skeleton hit");
    }

    private record PredictionObservation(float predictedHealth, float rawMin, float rawMax) {
    }

    private record DirectAttackObservation(boolean accepted, float health, int witherDuration) {
    }

    private record PopObservation(boolean accepted, float health, boolean totemConsumed, boolean witherApplied) {
    }
}
