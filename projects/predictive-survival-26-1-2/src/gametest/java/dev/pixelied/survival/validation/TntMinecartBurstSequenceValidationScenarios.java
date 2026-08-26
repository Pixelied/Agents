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

/** Exact-runtime unprimed TNT-minecart burst regressions. */
final class TntMinecartBurstSequenceValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    // MinecartTNT checks horizontal speed after Entity.move() zeros the collided axis. Keep a
    // tangential component so this is a real vanilla glancing-collision detonation, not a head-on
    // impact whose post-collision speed becomes zero.
    private static final Vec3 COLLISION_VELOCITY = new Vec3(0.5d, 0d, 1.2d);
    private static final Vec3 BURNING_ARROW_VELOCITY = new Vec3(1.5d, 0d, 0d);

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
            // Off-rail minecart motion is server-clamped. Starting close to the wall guarantees the
            // real server tick reaches it even after that clamp while still leaving a visible precursor.
            Vec3 cartPosition = new Vec3(center.getX() + 0.5d, center.getY(), center.getZ() + 2.4d);
            cart.setPos(cartPosition.x, cartPosition.y, cartPosition.z);
            cart.setNoGravity(true);
            cart.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(cart);
            return new Setup(
                victim.getUUID(), originalPosition, center, wall, cartPosition, cart.getId(), originals
            );
        });

        try {
            waitForClientPosition(context, setup.center());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.cartId()) instanceof MinecartTNT
                && minecraft.level.getBlockState(setup.wall()).is(Blocks.OBSIDIAN));

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            armTotemFromForecastCollision(context, singleplayer, setup, harness);

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

                cart.setPos(setup.cartPosition().x, setup.cartPosition().y, setup.cartPosition().z);
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
                throw new AssertionError("vanilla TNT minecart glancing-collision path did not explode/remove the unprimed cart");
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

    static void validateBurningArrowArmsBeforeUnprimedBurst(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        ArrowSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
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

            Vec3 arrowPosition = new Vec3(center.getX() - 1.0d, center.getY() + 0.35d, center.getZ() + 2.2d);
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

            return new ArrowSetup(
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

            ArrowOutcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                Entity cartEntity = level.getEntity(setup.cartId());
                Entity arrowEntity = level.getEntity(setup.arrowId());
                if (!(cartEntity instanceof MinecartTNT cart)) {
                    throw new AssertionError("TNT minecart disappeared before burning-arrow trigger");
                }
                if (!(arrowEntity instanceof Arrow arrow)) {
                    throw new AssertionError("burning arrow disappeared before TNT minecart trigger");
                }
                if (cart.isPrimed()) {
                    throw new AssertionError("burning-arrow burst must remain unprimed until the arrow damage itself");
                }
                if (!arrow.isOnFire()) {
                    throw new AssertionError("arrow lost its vanilla burning state before TNT minecart trigger");
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
                return new ArrowOutcome(
                    accepted,
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    cart.isRemoved()
                );
            });

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
                    ServerLevel level = (ServerLevel) victim.level();
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

    private static void armTotemFromForecastCollision(
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
            "tnt_minecart_forecast_collision_pre_arm"
        );

        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                Entity entity = ((ServerLevel) victim.level()).getEntity(setup.cartId());
                if (!(entity instanceof MinecartTNT cart)) {
                    throw new AssertionError("TNT minecart disappeared while maintaining collision precursor");
                }
                if (cart.isPrimed()) throw new AssertionError("collision precursor cart unexpectedly primed");
                cart.setPos(setup.cartPosition().x, setup.cartPosition().y, setup.cartPosition().z);
                cart.setDeltaMovement(COLLISION_VELOCITY);
                victim.connection.send(new ClientboundSetEntityMotionPacket(cart));
            });

            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.cartId()) instanceof MinecartTNT cart
                && cart.getDeltaMovement().x > 0.4d
                && cart.getDeltaMovement().z > 1.0d
                && !cart.isPrimed());

            context.runOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean opportunity = frame.opportunities().stream().anyMatch(candidate ->
                    candidate.family() == OpportunityFamily.TNT_MINECART
                        && "forecast_horizontal_collision".equals(candidate.evidence().get("trigger"))
                );
                if (!opportunity) {
                    var snapshot = frame.context().world().entities().stream()
                        .filter(entity -> entity.id().equals(Integer.toString(setup.cartId())))
                        .findFirst()
                        .orElse(null);
                    throw new AssertionError(
                        "unprimed TNT minecart collision precursor produced no forecast opportunity; snapshot="
                            + snapshot + " opportunities=" + frame.opportunities()
                    );
                }
                harness.engine().tick();
            });

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                Entity entity = ((ServerLevel) victim.level()).getEntity(setup.cartId());
                if (entity instanceof MinecartTNT cart) {
                    cart.setPos(setup.cartPosition().x, setup.cartPosition().y, setup.cartPosition().z);
                    cart.setDeltaMovement(Vec3.ZERO);
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
            "production engine did not establish server-authoritative protection from TNT minecart collision precursor"
        );
    }

    private static void armTotemFromBurningArrow(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        ArrowSetup setup,
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
                ServerLevel level = (ServerLevel) victim.level();
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
                victim.connection.send(new ClientboundSetEntityMotionPacket(arrow));
            });

            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.cartId()) instanceof MinecartTNT cart
                && !cart.isPrimed()
                && minecraft.level.getEntity(setup.arrowId()) instanceof Arrow arrow
                && arrow.isOnFire()
                && arrow.getDeltaMovement().x > 1.0d);

            context.runOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean opportunity = frame.opportunities().stream().anyMatch(candidate ->
                    candidate.family() == OpportunityFamily.TNT_MINECART
                        && "burning_arrow".equals(candidate.evidence().get("trigger"))
                );
                if (!opportunity) {
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
                harness.engine().tick();
            });

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
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
        Vec3 cartPosition,
        int cartId,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record ArrowSetup(
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
        float health,
        boolean protectionConsumed,
        boolean cartRemoved,
        boolean horizontalCollision
    ) {
    }

    private record ArrowOutcome(
        boolean attackAccepted,
        float health,
        boolean protectionConsumed,
        boolean cartRemoved
    ) {
    }
}
