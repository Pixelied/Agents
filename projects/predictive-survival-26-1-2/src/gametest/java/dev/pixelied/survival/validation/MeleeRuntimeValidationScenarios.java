package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class MeleeRuntimeValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private MeleeRuntimeValidationScenarios() {
    }

    static void validateWitherSkeletonParityFollowupAndProtection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int skeletonId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            prepareCleanInventory(player, 20f);
            ServerLevel level = (ServerLevel) player.level();

            WitherSkeleton skeleton = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
            skeleton.setPos(player.getX(), player.getY(), player.getZ() + 0.9d);
            skeleton.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(skeleton.blockPosition()),
                EntitySpawnReason.COMMAND,
                null
            );
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            skeleton.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(skeleton);

            if (Math.abs(skeleton.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() - 4.0d) > 0.0001d) {
                throw new AssertionError("finalized Wither Skeleton server ATTACK_DAMAGE base was not 4.0");
            }
            if (!skeleton.getMainHandItem().is(Items.STONE_SWORD)) {
                throw new AssertionError("finalized Wither Skeleton did not carry its vanilla stone sword");
            }
            return skeleton.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.player != null
                && minecraft.level.getEntity(skeletonId) instanceof WitherSkeleton skeleton
                && skeleton.getMainHandItem().is(Items.STONE_SWORD));

            Prediction prediction = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for Wither Skeleton validation");
                }
                MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
                SurvivalEngine.EngineFrame frame = runtime.capture();
                ThreatEvent direct = frame.timeline().events().stream()
                    .filter(event -> event.id().equals("melee:" + skeletonId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("real Wither Skeleton produced no direct melee threat"));
                boolean causalWither = frame.timeline().events().stream().anyMatch(event ->
                    event.damage().sourceKey().equals("minecraft:wither")
                        && event.requiresAcceptedEventId().orElse("").equals(direct.id())
                );
                if (!causalWither) {
                    throw new AssertionError("real Wither Skeleton direct hit produced no causal Wither follow-up");
                }
                float predictedHealth = new DamageSimulator().simulate(frame.context().player(), direct.damage()).after().health();
                return new Prediction(predictedHealth, direct.damage().rawDamage().min(), direct.damage().rawDamage().max());
            });

            DirectAttackOutcome actual = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                SurvivalValidationClientGameTest.reset(player, 20f);
                Entity entity = player.level().getEntity(skeletonId);
                if (!(entity instanceof WitherSkeleton skeleton)) {
                    throw new AssertionError("server Wither Skeleton disappeared before parity attack");
                }
                player.invulnerableTime = 0;
                boolean accepted = skeleton.doHurtTarget((ServerLevel) player.level(), player);
                MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
                return new DirectAttackOutcome(
                    accepted,
                    player.getHealth(),
                    wither == null ? 0 : wither.getDuration()
                );
            });
            if (!actual.accepted()) throw new AssertionError("real Wither Skeleton attack was not accepted");
            SurvivalValidationClientGameTest.assertClose(
                "wither_skeleton_direct_damage_parity",
                prediction.predictedHealth(),
                actual.health(),
                EPSILON
            );
            if (actual.witherDuration() != 200) {
                throw new AssertionError("Wither Skeleton follow-up duration expected=200 actual=" + actual.witherDuration());
            }
            if (prediction.rawMax() < prediction.rawMin() || prediction.rawMax() <= 0f) {
                throw new AssertionError("invalid Wither Skeleton reconstructed raw damage bound: " + prediction);
            }

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                prepareCleanInventory(player, 4f);
                player.getInventory().setItem(0, new ItemStack(Items.STICK));
                player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
                player.getInventory().setSelectedSlot(0);
                player.containerMenu.broadcastChanges();
            });
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.player.getHealth() <= 4f + EPSILON);

            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for Wither Skeleton protection validation");
                }
                MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
                SurvivalEngine engine = new SurvivalEngine(
                    SurvivalConfig.defaults(),
                    runtime,
                    new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
                );
                SurvivalEngine.EngineFrame frame = runtime.capture();
                ThreatEvent direct = frame.timeline().events().stream()
                    .filter(event -> event.id().equals("melee:" + skeletonId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("lethal Wither Skeleton disappeared from production timeline"));
                float unprotectedHealth = new DamageSimulator().simulate(frame.context().player(), direct.damage()).after().health();
                if (unprotectedHealth > 0f) {
                    throw new AssertionError("Wither Skeleton protection fixture was not lethal; predicted health=" + unprotectedHealth);
                }
                engine.tick();
            });

            waitForServerSelectedTotem(context, singleplayer, 1, "Wither Skeleton");

            ProtectedAttackOutcome protectedOutcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(skeletonId);
                if (!(entity instanceof WitherSkeleton skeleton)) {
                    throw new AssertionError("server Wither Skeleton disappeared before protected attack");
                }
                if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("Totem was not server-authoritative before Wither Skeleton attack");
                }
                player.invulnerableTime = 0;
                boolean accepted = skeleton.doHurtTarget((ServerLevel) player.level(), player);
                MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
                return new ProtectedAttackOutcome(
                    accepted,
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    wither == null ? 0 : wither.getDuration()
                );
            });
            if (!protectedOutcome.accepted()) throw new AssertionError("protected Wither Skeleton attack was not accepted");
            SurvivalValidationClientGameTest.assertClose("wither_skeleton_preemptive_totem", 1f, protectedOutcome.health(), EPSILON);
            if (!protectedOutcome.totemConsumed()) {
                throw new AssertionError("server-authoritative Totem was not consumed by lethal Wither Skeleton attack");
            }
            if (protectedOutcome.witherDuration() != 200) {
                throw new AssertionError("protected Wither Skeleton hit lost its causal 200-tick Wither follow-up");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity skeleton = player.level().getEntity(skeletonId);
                if (skeleton != null) skeleton.discard();
                prepareCleanInventory(player, 20f);
                player.containerMenu.broadcastChanges();
            });
            context.waitTick();
        }
    }

    private static void prepareCleanInventory(ServerPlayer player, float health) {
        SurvivalValidationClientGameTest.reset(player, health);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setDeltaMovement(Vec3.ZERO);
        player.containerMenu.broadcastChanges();
    }

    private static void waitForServerSelectedTotem(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int expectedSlot,
        String threat
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean armed = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getInventory().getSelectedSlot() == expectedSlot
                    && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
            });
            if (armed) return;
            context.waitTick();
        }
        throw new AssertionError("Predictive Survival did not make the Totem server-authoritative before " + threat);
    }

    private record Prediction(float predictedHealth, float rawMin, float rawMax) {
    }

    private record DirectAttackOutcome(boolean accepted, float health, int witherDuration) {
    }

    private record ProtectedAttackOutcome(boolean accepted, float health, boolean totemConsumed, int witherDuration) {
    }
}
