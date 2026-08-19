package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftSpecialThreatSnapshotAnnotator;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.GuardianBeamPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class GuardianBeamValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final float EPSILON = 0.0001f;

    private GuardianBeamValidationScenarios() {
    }

    static void validateActiveBeamProducesPreImpactSequence(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int guardianId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);

            Guardian guardian = new Guardian(EntityType.GUARDIAN, player.level());
            guardian.setPos(player.getX(), player.getY() + 1d, player.getZ() + 10d);
            guardian.setNoGravity(true);
            guardian.setDeltaMovement(Vec3.ZERO);
            player.level().addFreshEntity(guardian);
            guardian.setTarget(player);
            return guardian.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(guardianId) instanceof Guardian guardian
                && guardian.hasActiveAttackTarget()
                && guardian.getActiveAttackTarget() == minecraft.player);

            List<ThreatEvent> predicted = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for guardian beam validation");
                }
                PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                WorldSnapshot raw = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                WorldSnapshot world = new MinecraftSpecialThreatSnapshotAnnotator().annotate(
                    minecraft.level, minecraft.player, raw
                );
                PredictionContext predictionContext = new PredictionContext(
                    player,
                    world,
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                return new GuardianBeamPredictor().predict(predictionContext).stream()
                    .filter(event -> event.id().startsWith("guardian_beam:" + guardianId + ":"))
                    .toList();
            });

            if (predicted.size() != 2) {
                throw new AssertionError("active guardian beam did not produce magic+melee sequence: " + predicted);
            }
            int damageTick = waitForDamage(context, singleplayer);
            for (ThreatEvent event : predicted) {
                if (event.impact().earliest() > damageTick || event.impact().latest() < damageTick) {
                    throw new AssertionError(
                        "guardian beam window missed real damage tick; event=" + event.id()
                            + " predicted=" + event.impact() + " actual=" + damageTick
                    );
                }
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity guardian = player.level().getEntity(guardianId);
                if (guardian != null) guardian.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static int waitForDamage(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (int tick = 1; tick <= 100; tick++) {
            context.waitTick();
            float health = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            if (health < 20f - EPSILON) return tick;
        }
        throw new AssertionError("guardian beam did not damage the player within 100 ticks");
    }
}
