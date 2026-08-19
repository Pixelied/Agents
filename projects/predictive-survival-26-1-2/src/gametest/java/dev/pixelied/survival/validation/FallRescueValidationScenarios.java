package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.threat.FallPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FallRescueValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private FallRescueValidationScenarios() {
    }

    static List<ValidationResult> runtimeSlice(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        List<ValidationResult> results = new ArrayList<>();
        results.add(validateLiveLanding(context, singleplayer));
        results.add(validateWindChargeFallReset(singleplayer));
        validateMaceFallReset(singleplayer);
        results.add(validatePearlTeleportAndDamage(singleplayer));
        return List.copyOf(results);
    }

    private static ValidationResult validateLiveLanding(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        PlatformSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.resetCurrentImpulseContext();
            player.setDeltaMovement(Vec3.ZERO);

            ServerLevel level = (ServerLevel) player.level();
            BlockPos center = player.blockPosition().below();
            Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                }
            }

            double targetY = center.getY() + 12d;
            player.teleportTo(player.getX(), targetY, player.getZ());
            player.setDeltaMovement(0d, -0.25d, 0d);
            player.fallDistance = 0d;
            return new PlatformSetup(center.getY(), Map.copyOf(originals));
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getY() > setup.platformY + 7d
                && minecraft.player.getDeltaMovement().y < 0d);

            FallPrediction prediction = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable during live fall validation");
                }
                PlayerSnapshot snapshot = new MinecraftSnapshotFactory().capture(minecraft.player);
                PredictionContext predictionContext = new PredictionContext(
                    snapshot,
                    new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS),
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                ThreatEvent event = new FallPredictor().predict(predictionContext).stream()
                    .filter(candidate -> candidate.id().equals("fall:landing"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("live falling player produced no landing prediction"));
                float predictedHealth = SIMULATOR.simulate(snapshot, event.damage()).after().health();
                return new FallPrediction(event, snapshot.health(), predictedHealth);
            });

            int impactTicks = waitForHealthDrop(context, singleplayer, prediction.initialHealth, 80, "live_fall");
            long earliest = Math.max(0L, prediction.event.impact().earliest() - 1L);
            long latest = prediction.event.impact().latest() + 1L;
            if (impactTicks < earliest || impactTicks > latest) {
                throw new AssertionError(
                    "live_fall impact predicted=" + prediction.event.impact() + " actualTick=" + impactTicks
                );
            }

            float actualHealth = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            return new ValidationResult(
                "live_fall_landing",
                prediction.predictedHealth,
                actualHealth,
                ValidationStatus.RUNTIME_CONFIRMED,
                EPSILON
            );
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                for (Map.Entry<BlockPos, BlockState> entry : setup.originals.entrySet()) {
                    level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                }
            });
            context.waitTick();
        }
    }

    private static ValidationResult validateWindChargeFallReset(TestSingleplayerContext singleplayer) {
        float actualHealth = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.resetCurrentImpulseContext();
            player.fallDistance = 10d;
            ServerLevel level = (ServerLevel) player.level();
            WindCharge charge = new WindCharge(player, level, player.getX(), player.getY(), player.getZ());
            player.onExplosionHit(charge);
            if (!player.isIgnoringFallDamageFromCurrentImpulse()) {
                throw new AssertionError("player wind charge did not establish fall-damage impulse protection");
            }
            player.causeFallDamage(10d, 1f, player.damageSources().fall());
            return player.getHealth();
        });

        return new ValidationResult(
            "wind_charge_fall_reset",
            20f,
            actualHealth,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
        );
    }

    private static void validateMaceFallReset(TestSingleplayerContext singleplayer) {
        double remaining = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.fallDistance = 6d;
            ItemStack mace = new ItemStack(Items.MACE);
            player.setItemInHand(InteractionHand.MAIN_HAND, mace);
            ((MaceItem) Items.MACE).postHurtEnemy(mace, player, player);
            return player.fallDistance;
        });
        if (Math.abs(remaining) > 1.0E-9d) {
            throw new AssertionError("valid mace smash post-hit did not reset fall distance: " + remaining);
        }
    }

    private static ValidationResult validatePearlTeleportAndDamage(TestSingleplayerContext singleplayer) {
        PearlState state = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.resetCurrentImpulseContext();
            player.fallDistance = 10d;
            ServerLevel level = (ServerLevel) player.level();

            TestPearl pearl = new TestPearl(level, player);
            pearl.snapTo(player.getX(), player.getY() + 1d, player.getZ() + 1d);
            pearl.trigger(new Vec3(pearl.getX(), pearl.getY(), pearl.getZ()));
            return new PearlState(player.getHealth(), player.fallDistance, player.isIgnoringFallDamageFromCurrentImpulse());
        });

        if (Math.abs(state.fallDistance) > 1.0E-9d) {
            throw new AssertionError("Ender Pearl teleport did not reset fall distance: " + state.fallDistance);
        }
        if (state.impulseContextActive) {
            throw new AssertionError("Ender Pearl teleport did not reset impulse fall-damage context");
        }
        return new ValidationResult(
            "ender_pearl_teleport_raw_5",
            15f,
            state.health,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
        );
    }

    private static int waitForHealthDrop(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        float initialHealth,
        int maxTicks,
        String id
    ) {
        for (int tick = 1; tick <= maxTicks; tick++) {
            context.waitTick();
            float health = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            if (health < initialHealth) return tick;
        }
        throw new AssertionError(id + " did not damage the player within " + maxTicks + " ticks");
    }

    private record PlatformSetup(int platformY, Map<BlockPos, BlockState> originals) {
    }

    private record FallPrediction(ThreatEvent event, float initialHealth, float predictedHealth) {
    }

    private record PearlState(float health, double fallDistance, boolean impulseContextActive) {
    }

    private static final class TestPearl extends ThrownEnderpearl {
        private TestPearl(ServerLevel level, ServerPlayer owner) {
            super(level, owner, new ItemStack(Items.ENDER_PEARL));
        }

        private void trigger(Vec3 hitPosition) {
            super.onHit(new BlockHitResult(
                hitPosition,
                Direction.UP,
                BlockPos.containing(hitPosition),
                false
            ));
        }
    }
}
