package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Exact-runtime proof that client-optimistic Elytra state is not server movement authority. */
final class ElytraAuthorityRaceValidationScenarios {
    private static final double POSITION_EPSILON = 0.05d;

    private ElytraAuthorityRaceValidationScenarios() {
    }

    static void validateOptimisticStartIsRejectedWithoutMovementAuthority(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 originalPosition = player.position();
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setNoGravity(true);
            player.setOnGround(false);
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            Vec3 airborne = new Vec3(originalPosition.x, 326d, originalPosition.z);
            player.teleportTo(airborne.x, airborne.y, airborne.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.containerMenu.broadcastChanges();
            return new Setup(player.getUUID(), originalPosition, airborne);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                && !minecraft.player.onGround()
                && !minecraft.player.isFallFlying()
                && Math.abs(minecraft.player.getX() - setup.airborne().x) <= POSITION_EPSILON
                && Math.abs(minecraft.player.getY() - setup.airborne().y) <= POSITION_EPSILON
                && Math.abs(minecraft.player.getZ() - setup.airborne().z) <= POSITION_EPSILON);

            Optimistic optimistic = context.computeOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client player disappeared before Elytra race");
                boolean started = minecraft.player.tryToStartFallFlying();
                boolean moveDispatched = new MinecraftCommandDispatcher().dispatch(
                    minecraft,
                    new ExecutionCommand.MoveToward(new Vec3Snapshot(
                        minecraft.player.getX() + 4d,
                        minecraft.player.getY(),
                        minecraft.player.getZ()
                    ))
                );
                return new Optimistic(started, minecraft.player.isFallFlying(), moveDispatched);
            });
            if (!optimistic.started() || !optimistic.clientFallFlying()) {
                throw new AssertionError("real client did not enter the optimistic Elytra state: " + optimistic);
            }
            if (optimistic.moveDispatched()) {
                throw new AssertionError("production dispatcher claimed unauthoritative Elytra movement succeeded");
            }

            Rejection rejection = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(setup.playerId());
                if (player == null) throw new AssertionError("server player disappeared during Elytra race");
                if (player.isFallFlying()) {
                    throw new AssertionError("client optimistic fall-flying leaked into server entity state");
                }

                // Clear setup dirtiness so the packet below proves the rejection flag itself dirties
                // shared entity data even though its final value returns to the default false state.
                player.getEntityData().packDirty();
                player.setOnGround(true);
                player.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(
                    player,
                    ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
                ));
                boolean accepted = player.isFallFlying();
                var dirty = player.getEntityData().packDirty();
                if (dirty == null || dirty.isEmpty()) {
                    throw new AssertionError("server Elytra rejection produced no dirty entity-data correction");
                }
                player.connection.send(new ClientboundSetEntityDataPacket(player.getId(), dirty));
                return new Rejection(accepted, dirty.size());
            });
            if (rejection.serverAccepted()) {
                throw new AssertionError("server accepted START_FALL_FLYING despite authoritative on-ground state");
            }

            context.waitFor(minecraft -> minecraft.player != null && !minecraft.player.isFallFlying());
            boolean corrected = context.computeOnClient(minecraft -> minecraft.player != null
                && !minecraft.player.isFallFlying());
            if (!corrected) {
                throw new AssertionError("server fall-flying rejection did not clear the optimistic client flag");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(setup.playerId());
                if (player == null) return;
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setNoGravity(false);
                player.setOnGround(false);
                player.teleportTo(
                    setup.originalPosition().x,
                    setup.originalPosition().y,
                    setup.originalPosition().z
                );
                player.setDeltaMovement(Vec3.ZERO);
                player.containerMenu.broadcastChanges();
            });
            context.waitTick();
        }
    }

    private record Setup(UUID playerId, Vec3 originalPosition, Vec3 airborne) {
    }

    private record Optimistic(boolean started, boolean clientFallFlying, boolean moveDispatched) {
    }

    private record Rejection(boolean serverAccepted, int dirtyValues) {
    }
}
