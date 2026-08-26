package dev.pixelied.survival.validation;

import com.mojang.authlib.GameProfile;
import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

final class BurstSequenceValidationSupport {
    private BurstSequenceValidationSupport() {
    }

    static RuntimeHarness newHarness(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> {
            MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
            SurvivalEngine engine = new SurvivalEngine(
                SurvivalConfig.defaults(),
                runtime,
                new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
            );
            return new RuntimeHarness(runtime, engine);
        });
    }

    static void prepareVictim(ServerPlayer player, float health) {
        SurvivalValidationClientGameTest.reset(player, health);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0d;
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.STICK));
        player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.containerMenu.broadcastChanges();
    }

    static void armTotemFromPrecursor(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        RuntimeHarness harness,
        String id
    ) {
        ensureSelectedSlot(context, singleplayer, 0, id + "_pre_arm");
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            context.runOnClient(minecraft -> harness.engine().tick());
            context.waitTick();
            boolean protectedOnServer = singleplayer.getServer().computeOnServer(server ->
                protectedInHand(SurvivalValidationClientGameTest.onlyPlayer(server))
            );
            if (protectedOnServer) return;
        }
        throw new AssertionError(
            "production engine did not establish server-authoritative protection from " + id + " precursor"
        );
    }

    static boolean protectedInHand(ServerPlayer player) {
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
            || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    static boolean protectionConsumed(ServerPlayer player) {
        return !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
            && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    static void ensureSelectedSlot(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int slot,
        String id
    ) {
        context.runOnClient(minecraft -> {
            if (minecraft.player == null) {
                throw new AssertionError("client victim unavailable while selecting slot for " + id);
            }
            if (!new MinecraftCommandDispatcher().dispatch(minecraft, new ExecutionCommand.SelectHotbar(slot))) {
                throw new AssertionError("could not dispatch hotbar selection for " + id);
            }
        });
        context.waitFor(minecraft -> minecraft.player != null && minecraft.player.getInventory().getSelectedSlot() == slot);
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean confirmed = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getInventory().getSelectedSlot() == slot
            );
            if (confirmed) return;
            context.waitTick();
        }
        throw new AssertionError("server did not confirm slot " + slot + " for " + id);
    }

    static AttackerHandle createMockAttacker(net.minecraft.server.MinecraftServer server, ServerPlayer victim) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "burst-attacker"),
            false
        );
        ServerPlayer attacker = new ServerPlayer(
            server,
            (net.minecraft.server.level.ServerLevel)victim.level(),
            cookie.gameProfile(),
            cookie.clientInformation()
        ) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(new ChannelHandler[]{connection});
        server.getPlayerList().placeNewPlayer(connection, attacker, cookie);
        attacker.setNoGravity(true);
        attacker.setDeltaMovement(Vec3.ZERO);
        return new AttackerHandle(attacker.getUUID(), attacker.getId());
    }

    static ServerPlayer requireAttacker(net.minecraft.server.MinecraftServer server, AttackerHandle handle) {
        ServerPlayer player = server.getPlayerList().getPlayer(handle.playerId());
        if (player == null) throw new AssertionError("mock burst attacker disappeared");
        return player;
    }

    static void syncEquipment(ServerPlayer observer, ServerPlayer remote) {
        observer.connection.send(new ClientboundSetEquipmentPacket(
            remote.getId(),
            List.of(
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, remote.getMainHandItem().copy()),
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND, remote.getOffhandItem().copy())
            )
        ));
    }

    static void waitForClientAttacker(ClientGameTestContext context, AttackerHandle handle) {
        context.waitFor(minecraft -> minecraft.level != null
            && minecraft.level.getEntity(handle.entityId()) instanceof net.minecraft.world.entity.player.Player);
    }

    static void removeMockAttacker(net.minecraft.server.MinecraftServer server, AttackerHandle handle) {
        ServerPlayer attacker = server.getPlayerList().getPlayer(handle.playerId());
        if (attacker != null) server.getPlayerList().remove(attacker);
    }

    record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    record AttackerHandle(UUID playerId, int entityId) {
    }
}
