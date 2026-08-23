package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalPlanner;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class WitherSkeletonValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private WitherSkeletonValidationScenarios() {
    }

    static void validateSourceFaithfulMeleeAndPreemptiveProtection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int skeletonId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level, EntitySpawnReason.TRIGGERED);
            if (skeleton == null) throw new AssertionError("could not create Wither Skeleton fixture");
            skeleton.snapTo(player.getX(), player.getY(), player.getZ() + 1.2d, 0f, 0f);
            skeleton.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(skeleton.blockPosition()),
                EntitySpawnReason.TRIGGERED,
                null
            );
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            level.addFreshEntity(skeleton);
            return skeleton.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(skeletonId) instanceof WitherSkeleton);
            context.waitTick();

            Prediction prediction = context.computeOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client player unavailable for Wither Skeleton validation");
                MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
                SurvivalEngine.EngineFrame frame = runtime.capture();
                ThreatEvent direct = frame.timeline().events().stream()
                    .filter(event -> event.id().equals("melee:" + skeletonId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("real Wither Skeleton produced no melee threat"));
                ThreatEvent wither = frame.timeline().events().stream()
                    .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
                    .filter(event -> event.requiresAcceptedEventId().orElse("").equals(direct.id()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Wither Skeleton direct hit produced no causal Wither follow-up"));
                float predictedHealth = new DamageSimulator().simulate(frame.context().player(), direct.damage()).after().health();
                return new Prediction(
                    direct.damage().rawDamage().min(),
                    direct.damage().rawDamage().max(),
                    predictedHealth,
                    direct.id(),
                    wither.requiresAcceptedEventId().orElse(""),
                    wither.damage().sourceKey()
                );
            });

            DirectHit actual = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
                WitherSkeleton skeleton = requireSkeleton(player, skeletonId);
                skeleton.snapTo(player.getX(), player.getY(), player.getZ() + 1.2d, 0f, 0f);
                boolean accepted = skeleton.doHurtTarget((ServerLevel) player.level(), player);
                MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
                return new DirectHit(
                    accepted,
                    player.getHealth(),
                    wither == null ? -1 : wither.getDuration(),
                    wither == null ? -1 : wither.getAmplifier()
                );
            });

            if (!actual.accepted()) throw new AssertionError("real Wither Skeleton direct hit was unexpectedly rejected");
            SurvivalValidationClientGameTest.assertClose(
                "wither_skeleton_direct_damage",
                prediction.predictedHealth(),
                actual.health(),
                EPSILON
            );
            if (prediction.rawMin() > 8f + EPSILON || prediction.rawMax() < 8f - EPSILON) {
                throw new AssertionError("Wither Skeleton reconstructed raw damage did not contain vanilla 8: " + prediction);
            }
            if (actual.witherDuration() != 200 || actual.witherAmplifier() != 0) {
                throw new AssertionError("Wither Skeleton follow-up mismatch: " + actual);
            }
            if (!prediction.directId().equals(prediction.witherPrerequisite())
                || !"minecraft:wither".equals(prediction.witherSource())) {
                throw new AssertionError("Wither follow-up was not causally attached to the accepted direct hit: " + prediction);
            }

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                SurvivalValidationClientGameTest.reset(player, 4f);
                player.setDeltaMovement(Vec3.ZERO);
                player.getFoodData().setFoodLevel(6);
                player.getFoodData().setSaturation(0f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, new ItemStack(Items.STICK));
                player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
                WitherSkeleton skeleton = requireSkeleton(player, skeletonId);
                skeleton.snapTo(player.getX(), player.getY(), player.getZ() + 1.2d, 0f, 0f);
                player.containerMenu.broadcastChanges();
            });

            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.level != null
                && minecraft.level.getEntity(skeletonId) instanceof WitherSkeleton);

            // Keep the controlled lethal state stable while the real production engine is exercised.
            // Vanilla natural regeneration can otherwise turn this into a non-lethal scenario while
            // the test is still polling for server-authoritative inventory confirmation.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                player.invulnerableTime = 0;
                player.setHealth(4f);
            });
            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getHealth() - 4f) <= EPSILON);

            RuntimeHarness harness = context.computeOnClient(minecraft -> {
                MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
                SurvivalEngine engine = new SurvivalEngine(
                    SurvivalConfig.defaults(),
                    runtime,
                    new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
                );
                return new RuntimeHarness(runtime, engine);
            });

            boolean protectedOnServer = false;
            for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
                context.runOnClient(minecraft -> harness.engine().tick());
                context.waitTick();
                protectedOnServer = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
                });
                if (protectedOnServer) break;
            }
            if (!protectedOnServer) {
                String clientDiagnostic = context.computeOnClient(minecraft -> {
                    var frame = harness.runtime().lastFrame().orElse(null);
                    var snapshot = frame == null ? null : frame.context().player();
                    var timelineSimulator = new ThreatTimelineSimulator();
                    var baseline = frame == null ? null : timelineSimulator.simulate(snapshot, frame.timeline());
                    ThreatEvent direct = frame == null ? null : frame.timeline().events().stream()
                        .filter(event -> event.id().equals("melee:" + skeletonId))
                        .findFirst()
                        .orElse(null);
                    var directDamage = snapshot == null || direct == null
                        ? null
                        : new DamageSimulator().simulate(snapshot, direct.damage());
                    var candidate = frame == null || frame.candidates().isEmpty() ? null : frame.candidates().getFirst();
                    var candidateSimulation = frame == null || candidate == null
                        ? null
                        : new SurvivalPlanner().simulate(frame.context(), frame.timeline(), candidate, SafetyMode.SAFE);
                    var immediateCandidateResult = snapshot == null || frame == null || candidate == null
                        ? null
                        : timelineSimulator.simulate(candidate.apply(snapshot), frame.timeline());
                    return "clientSelected=" + (minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot())
                        + ", clientHealth=" + (minecraft.player == null ? -1f : minecraft.player.getHealth())
                        + ", snapshotHealth=" + (snapshot == null ? "none" : Float.toString(snapshot.health()))
                        + ", snapshotAbsorption=" + (snapshot == null ? "none" : Float.toString(snapshot.absorption()))
                        + ", snapshotDifficulty=" + (snapshot == null ? "none" : snapshot.difficulty())
                        + ", snapshotPlayerInvulnerable=" + (snapshot == null ? "none" : snapshot.playerInvulnerable())
                        + ", snapshotAbilityInvulnerable=" + (snapshot == null ? "none" : snapshot.abilityInvulnerable())
                        + ", snapshotDeadOrDying=" + (snapshot == null ? "none" : snapshot.deadOrDying())
                        + ", snapshotMitigation=" + (snapshot == null ? "none" : snapshot.mitigation())
                        + ", snapshotEffects=" + (snapshot == null ? "none" : snapshot.statusEffects())
                        + ", snapshotBlocking=" + (snapshot == null ? "none" : snapshot.blocking())
                        + ", snapshotHurtState=" + (snapshot == null ? "none" : snapshot.hurtState())
                        + ", snapshotDeathProtection=" + (snapshot == null ? "none" : snapshot.deathProtection())
                        + ", directDamage=" + directDamage
                        + ", baseline=" + baseline
                        + ", candidateSimulation=" + candidateSimulation
                        + ", immediateCandidateResult=" + immediateCandidateResult
                        + ", currentPlan=" + harness.engine().currentPlan()
                        + ", executionStatus=" + harness.engine().executionStatus()
                        + ", history=" + harness.engine().history().snapshot()
                        + ", timeline=" + (frame == null ? "none" : frame.timeline().events())
                        + ", candidates=" + (frame == null ? "none" : frame.candidates());
                });
                String serverDiagnostic = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    return "serverSelected=" + player.getInventory().getSelectedSlot()
                        + ", serverHealth=" + player.getHealth()
                        + ", serverAbsorption=" + player.getAbsorptionAmount()
                        + ", serverInvulnerableTime=" + player.invulnerableTime
                        + ", serverMain=" + player.getMainHandItem()
                        + ", serverOffhand=" + player.getOffhandItem();
                });
                throw new AssertionError(
                    "production engine did not make a Totem server-authoritative before preventable Wither Skeleton melee; "
                        + clientDiagnostic + "; " + serverDiagnostic
                );
            }

            ProtectedHit protectedHit = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server lost Totem authority before controlled Wither Skeleton attack");
                }
                player.invulnerableTime = 0;
                player.setHealth(4f);
                WitherSkeleton skeleton = requireSkeleton(player, skeletonId);
                boolean accepted = skeleton.doHurtTarget((ServerLevel) player.level(), player);
                MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
                return new ProtectedHit(
                    accepted,
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    wither == null ? -1 : wither.getDuration()
                );
            });
            if (!protectedHit.accepted()) throw new AssertionError("controlled lethal Wither Skeleton attack was rejected");
            SurvivalValidationClientGameTest.assertClose("wither_skeleton_preemptive_totem", 1f, protectedHit.health(), EPSILON);
            if (!protectedHit.totemConsumed() || protectedHit.witherDuration() != 200) {
                throw new AssertionError("server-authoritative Totem/Wither outcome mismatch: " + protectedHit);
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity skeleton = player.level().getEntity(skeletonId);
                if (skeleton != null) skeleton.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            });
            context.runOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client player unavailable during Wither cleanup");
                boolean dispatched = new MinecraftCommandDispatcher().dispatch(
                    minecraft,
                    new ExecutionCommand.SelectHotbar(0)
                );
                if (!dispatched) throw new AssertionError("could not restore client hotbar selection after Wither regression");
            });
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getSelectedSlot() == 0);
            context.waitTick();
        }
    }

    private static WitherSkeleton requireSkeleton(ServerPlayer player, int skeletonId) {
        Entity entity = player.level().getEntity(skeletonId);
        if (!(entity instanceof WitherSkeleton skeleton)) {
            throw new AssertionError("Wither Skeleton fixture disappeared: " + skeletonId);
        }
        return skeleton;
    }

    private record Prediction(
        float rawMin,
        float rawMax,
        float predictedHealth,
        String directId,
        String witherPrerequisite,
        String witherSource
    ) {
    }

    private record DirectHit(boolean accepted, float health, int witherDuration, int witherAmplifier) {
    }

    private record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    private record ProtectedHit(boolean accepted, float health, boolean totemConsumed, int witherDuration) {
    }
}
