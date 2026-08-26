package dev.pixelied.survival.validation;

import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime unprimed TNT-minecart burst regressions. */
final class TntMinecartBurstSequenceValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final Vec3 COLLISION_VELOCITY = new Vec3(0d, 0d, 1.2d);

    private TntMinecartBurstSequenceValidationScenarios() {
    }

    static void validateForecastCollisionArmsBeforeUnprimedBurst(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 280d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);
            BlockPos wall = center.offset(0, 0, 3);
            level.setBlockAndUpdate(wall, Blocks.OBSIDIAN.defaultBlockState());

            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            MinecartTNT cart = new MinecartTNT(EntityType.TNT_MINECART, level);
            cart.setPos(center.getX() + 0.5d, center.getY(), center.getZ() + 2.0d);
            cart.setNoGravity(true);
            cart.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(cart);
            return new Setup(victim.getUUID(), originalPosition, center, wall, cart.getId(), originals);
        });

        try {
            waitForClientPosition(context, setup.center());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.cartId()) instanceof MinecartTNT
                && minecraft.level.getBlockState(setup.wall()).is(Blocks.OBSIDIAN));

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                Entity entity = ((ServerLevel) victim.level()).getEntity(setup.cartId());
                if (!(entity instanceof MinecartTNT cart)) {
                    throw new AssertionError("TNT minecart disappeared before precursor motion sync");
                }
                if (cart.isPrimed()) throw new AssertionError("collision precursor cart unexpectedly primed");
                cart.setDeltaMovement(COLLISION_VELOCITY);
                victim.connection.send(new ClientboundSetEntityMotionPacket(cart));
                cart.setDeltaMovement(Vec3.ZERO);
            });

            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.cartId()) instanceof MinecartTNT cart
                && cart.getDeltaMovement().z > 1.0d
                && !cart.isPrimed());

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            context.runOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean opportunity = frame.opportunities().stream().anyMatch(candidate ->
                    candidate.family() == OpportunityFamily.TNT_MINECART
                        && "forecast_horizontal_collision".equals(candidate.evidence().get("trigger"))
                );
                if (!opportunity) {
                    throw new AssertionError(
                        "unprimed TNT minecart collision precursor produced no forecast opportunity: "
                            + frame.opportunities()
                    );
                }
            });

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "tnt_minecart_forecast_collision"
            );

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                Entity entity = level.getEntity(setup.cartId());
                if (!(entity instanceof MinecartTNT cart)) {
                    throw new AssertionError("TNT minecart disappeared before zero-delay collision trigger");
                }
                if (cart.isPrimed()) {
                    throw new AssertionError("collision burst must remain unprimed until the collision itself");
                }
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before TNT minecart collision");
                }

                victim.invulnerableTime = 0;
                victim.setHealth(4f);
                cart.setDeltaMovement(COLLISION_VELOCITY);
                cart.tick();
                return new Outcome(
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    cart.isRemoved(),
                    cart.horizontalCollision
                );
            });

            if (!outcome.cartRemoved()) {
                throw new AssertionError("vanilla TNT minecart collision path did not explode/remove the unprimed cart");
            }
            if (!outcome.horizontalCollision()) {
                throw new AssertionError("TNT minecart did not report the source-required horizontal collision");
            }
            SurvivalValidationClientGameTest.assertClose("tnt_minecart_collision_pop", 1f, outcome.health(), EPSILON);
            if (!outcome.protectionConsumed()) {
                throw new AssertionError("TNT minecart collision burst did not consume server-authoritative protection");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel) victim.level();
                    Entity entity = level.getEntity(setup.cartId());
                    if (entity != null) entity.discard();
                    restore(level, setup.originals());
                    SurvivalValidationClientGameTest.reset(victim, 20f);
                    victim.setNoGravity(false);
                    victim.teleportTo(
                        setup.originalPosition().x,
                        setup.originalPosition().y,
                        setup.originalPosition().z
                    );
                }
            });
            context.waitTick();
        }
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 5; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return Map.copyOf(originals);
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    private static void waitForClientPosition(ClientGameTestContext context, BlockPos center) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - (center.getX() + 0.5d)) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - center.getY()) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - (center.getZ() + 0.5d)) <= POSITION_EPSILON);
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        BlockPos wall,
        int cartId,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record Outcome(
        float health,
        boolean protectionConsumed,
        boolean cartRemoved,
        boolean horizontalCollision
    ) {
    }
}
