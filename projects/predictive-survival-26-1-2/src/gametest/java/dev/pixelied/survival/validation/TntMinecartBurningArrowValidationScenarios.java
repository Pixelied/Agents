package dev.pixelied.survival.validation;

import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Proves a visible burning-arrow trajectory arms protection before an unprimed TNT-minecart hit. */
final class TntMinecartBurningArrowValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final Vec3 BURNING_ARROW_VELOCITY = new Vec3(1.5d, 0d, 0d);

    private TntMinecartBurningArrowValidationScenarios() {
    }

    static void validateBurningArrowArmsBeforeUnprimedBurst(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 300d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            MinecartTNT cart = new MinecartTNT(EntityType.TNT_MINECART, level);
            Vec3 cartPosition = new Vec3(center.getX() + 0.5d, center.getY(), center.getZ() + 2.2d);
            cart.setPos(cartPosition.x, cartPosition.y, cartPosition.z);
            cart.setNoGravity(true);
            cart.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(cart);

            // 4.5 blocks center-to-center at 1.5 blocks/tick is a three-tick precursor. The
            // GameTest deliberately spends one client tick processing packets before capture, so
            // the production predictor must still see the remaining two-tick authority window.
            Vec3 arrowPosition = new Vec3(center.getX() - 4.0d, center.getY() + 0.35d, center.getZ() + 2.2d);
            Arrow arrow = new Arrow(
                level,
                arrowPosition.x,
                arrowPosition.y,
                arrowPosition.z,
                new ItemStack(Items.ARROW),
                null
            );
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(Vec3.ZERO);
            arrow.igniteForTicks(200);
            arrow.setSharedFlagOnFire(true);
            level.addFreshEntity(arrow);

            return new Setup(
                victim.getUUID(),
                originalPosition,
                center,
                cartPosition,
                arrowPosition,
                cart.getId(),
                arrow.getId(),
                originals
            );
        });

        try {
            waitForClientPosition(context, setup.center());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.cartId()) instanceof MinecartTNT cart
                && !cart.isPrimed()
                && minecraft.level.getEntity(setup.arrowId()) instanceof Arrow arrow
                && arrow.isOnFire());

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            armTotemFromBurningArrow(context, singleplayer, setup, harness);

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel)victim.level();
                Entity cartEntity = level.getEntity(setup.cartId());
                Entity arrowEntity = level.getEntity(setup.arrowId());
                if (!(cartEntity instanceof MinecartTNT cart)) {
                    throw new AssertionError("TNT minecart disappeared before burning-arrow trigger");
                }
                if (!(arrowEntity instanceof Arrow arrow)) {
                    throw new AssertionError("burning arrow disappeared before TNT minecart trigger");
                }
                if (cart.isPrimed()) {
                    throw new AssertionError("burning-arrow burst must remain unprimed until arrow damage");
                }
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before burning-arrow TNT burst");
                }

                cart.setPos(setup.cartPosition().x, setup.cartPosition().y, setup.cartPosition().z);
                cart.setDeltaMovement(Vec3.ZERO);
                arrow.setPos(setup.arrowPosition().x, setup.arrowPosition().y, setup.arrowPosition().z);
                arrow.setDeltaMovement(BURNING_ARROW_VELOCITY);
                arrow.igniteForTicks(100);
                arrow.setSharedFlagOnFire(true);
                victim.invulnerableTime = 0;
                victim.setHealth(4f);

                boolean accepted = cart.hurtServer(
                    level,
                    victim.damageSources().arrow(arrow, null),
                    1f
                );
                return new Outcome(
                    accepted,
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    cart.isRemoved()
                );
            });

            if (!outcome.attackAccepted()) {
                throw new AssertionError("vanilla burning-arrow TNT minecart damage was rejected");
            }
            if (!outcome.cartRemoved()) {
                throw new AssertionError("vanilla burning-arrow path did not immediately explode/remove unprimed TNT minecart");
            }
            SurvivalValidationClientGameTest.assertClose("tnt_minecart_burning_arrow_pop", 1f, outcome.health(), EPSILON);
            if (!outcome.protectionConsumed()) {
                throw new AssertionError("burning-arrow TNT minecart burst did not consume server-authoritative protection");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel)victim.level();
                    Entity cart = level.getEntity(setup.cartId());
                    if (cart != null) cart.discard();
                    Entity arrow = level.getEntity(setup.arrowId());
                    if (arrow != null) arrow.discard();
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

    private static void armTotemFromBurningArrow(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Setup setup,
        BurstSequenceValidationSupport.RuntimeHarness harness
    ) {
        BurstSequenceValidationSupport.ensureSelectedSlot(
            context,
            singleplayer,
            setup.victimId(),
            0,
            "tnt_minecart_burning_arrow_pre_arm"
        );

        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel)victim.level();
                Entity cartEntity = level.getEntity(setup.cartId());
                Entity arrowEntity = level.getEntity(setup.arrowId());
                if (!(cartEntity instanceof MinecartTNT cart) || !(arrowEntity instanceof Arrow arrow)) {
                    throw new AssertionError("TNT minecart or burning arrow disappeared while maintaining precursor");
                }
                if (cart.isPrimed()) throw new AssertionError("burning-arrow precursor cart unexpectedly primed");

                cart.setPos(setup.cartPosition().x, setup.cartPosition().y, setup.cartPosition().z);
                cart.setDeltaMovement(Vec3.ZERO);
                arrow.setPos(setup.arrowPosition().x, setup.arrowPosition().y, setup.arrowPosition().z);
                arrow.igniteForTicks(100);
                arrow.setSharedFlagOnFire(true);
                arrow.setDeltaMovement(BURNING_ARROW_VELOCITY);
                victim.connection.send(ClientboundEntityPositionSyncPacket.of(arrow));
                victim.connection.send(new ClientboundSetEntityMotionPacket(arrow));
            });

            // Spend one real client tick processing the vanilla packets and projectile simulation.
            // The arrow began three ticks away, so a valid opportunity must still have enough lead
            // to complete the fastest one-packet Totem route from this captured frame.
            context.waitTick();
            context.runOnClient(minecraft -> {
                if (minecraft.level == null || !(minecraft.level.getEntity(setup.arrowId()) instanceof Arrow arrow)) {
                    throw new AssertionError("client burning arrow disappeared before authority-horizon capture");
                }
                if (!arrow.isOnFire()) {
                    throw new AssertionError("client burning-arrow precursor lost synchronized fire state");
                }
                if (minecraft.level.getEntity(setup.cartId()) instanceof MinecartTNT cart && cart.isPrimed()) {
                    throw new AssertionError("client burning-arrow precursor cart unexpectedly primed");
                }

                var frame = harness.runtime().capture();
                var opportunity = frame.opportunities().stream()
                    .filter(candidate -> candidate.family() == OpportunityFamily.TNT_MINECART)
                    .filter(candidate -> "burning_arrow".equals(candidate.evidence().get("trigger")))
                    .findFirst()
                    .orElse(null);
                if (opportunity == null) {
                    var cartSnapshot = frame.context().world().entities().stream()
                        .filter(entity -> entity.id().equals(Integer.toString(setup.cartId())))
                        .findFirst()
                        .orElse(null);
                    var arrowSnapshot = frame.context().world().entities().stream()
                        .filter(entity -> entity.id().equals(Integer.toString(setup.arrowId())))
                        .findFirst()
                        .orElse(null);
                    throw new AssertionError(
                        "burning-arrow TNT minecart precursor produced no opportunity; cart=" + cartSnapshot
                            + " arrow=" + arrowSnapshot + " opportunities=" + frame.opportunities()
                    );
                }

                long fastestProtectionAuthorityTick = Math.max(
                    0L,
                    frame.context().timing().deadline(1).completionWindow().latest()
                        - frame.context().timing().clientTick()
                );
                if (opportunity.projectedThreat().impact().earliest() < fastestProtectionAuthorityTick) {
                    throw new AssertionError(
                        "burning-arrow precursor was observed only after the fastest Totem guarantee was lost; "
                            + "impact=" + opportunity.projectedThreat().impact()
                            + " authorityTick=" + fastestProtectionAuthorityTick
                            + " evidence=" + opportunity.evidence()
                            + " arrowPos=" + arrow.position()
                            + " arrowVelocity=" + arrow.getDeltaMovement()
                    );
                }
                harness.engine().tick();
            });

            // Park and re-synchronize the projectile immediately after the planning frame. This
            // prevents client projectile prediction from hitting/removing the arrow while the
            // outbound Totem selection is crossing the real client/server boundary.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel)victim.level();
                Entity cartEntity = level.getEntity(setup.cartId());
                if (cartEntity instanceof MinecartTNT cart) {
                    cart.setPos(setup.cartPosition().x, setup.cartPosition().y, setup.cartPosition().z);
                    cart.setDeltaMovement(Vec3.ZERO);
                }
                Entity arrowEntity = level.getEntity(setup.arrowId());
                if (arrowEntity instanceof Arrow arrow) {
                    arrow.setPos(setup.arrowPosition().x, setup.arrowPosition().y, setup.arrowPosition().z);
                    arrow.setDeltaMovement(Vec3.ZERO);
                    arrow.igniteForTicks(100);
                    arrow.setSharedFlagOnFire(true);
                    victim.connection.send(ClientboundEntityPositionSyncPacket.of(arrow));
                    victim.connection.send(new ClientboundSetEntityMotionPacket(arrow));
                }
            });
            context.waitTick();

            boolean protectedOnServer = singleplayer.getServer().computeOnServer(server ->
                BurstSequenceValidationSupport.protectedInHand(
                    BurstSequenceValidationSupport.requireVictim(server, setup.victimId())
                )
            );
            if (protectedOnServer) return;
        }
        throw new AssertionError(
            "production engine did not establish server-authoritative protection from burning-arrow TNT minecart precursor"
        );
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -6; dx <= 4; dx++) {
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
        Vec3 cartPosition,
        Vec3 arrowPosition,
        int cartId,
        int arrowId,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record Outcome(
        boolean attackAccepted,
        float health,
        boolean protectionConsumed,
        boolean cartRemoved
    ) {
    }
}
