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
import net.minecraft.world.entity.ai.attributes.Attributes;
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
        UUID victimId,
        RuntimeHarness harness,
        String id
    ) {
        ensureSelectedSlot(context, singleplayer, victimId, 0, id + "_pre_arm");
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            context.runOnClient(minecraft -> harness.engine().tick());
            context.waitTick();
            boolean protectedOnServer = singleplayer.getServer().computeOnServer(server ->
                protectedInHand(requireVictim(server, victimId))
            );
            if (protectedOnServer) return;
        }

        String clientDiagnostics = context.computeOnClient(minecraft -> {
            var frame = harness.runtime().capture();
            String inventory;
            if (minecraft.player == null) {
                inventory = "player=null";
            } else {
                inventory = "selected=" + minecraft.player.getInventory().getSelectedSlot()
                    + ",slot0=" + minecraft.player.getInventory().getItem(0)
                    + ",slot1=" + minecraft.player.getInventory().getItem(1)
                    + ",main=" + minecraft.player.getMainHandItem()
                    + ",off=" + minecraft.player.getOffhandItem();
            }
            return "actual=" + frame.actualTimeline().events().stream()
                .map(event -> event.kind() + ":" + event.id())
                .toList()
                + ",opportunities=" + frame.opportunities().stream()
                    .map(opportunity -> opportunity.family() + ":" + opportunity.id())
                    .toList()
                + ",planning=" + frame.planningTimeline().events().stream()
                    .map(event -> event.kind() + ":" + event.id())
                    .toList()
                + ",candidates=" + frame.candidates().stream()
                    .map(action -> action.getClass().getSimpleName() + ":" + action)
                    .toList()
                + ",currentPlan=" + harness.engine().currentPlan()
                + ",executionStatus=" + harness.engine().executionStatus()
                + ",history=" + harness.engine().history().snapshot()
                + ",inventory={" + inventory + "}";
        });
        String serverDiagnostics = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = requireVictim(server, victimId);
            return "selected=" + victim.getInventory().getSelectedSlot()
                + ",slot0=" + victim.getInventory().getItem(0)
                + ",slot1=" + victim.getInventory().getItem(1)
                + ",main=" + victim.getMainHandItem()
                + ",off=" + victim.getOffhandItem();
        });
        throw new AssertionError(
            "production engine did not establish server-authoritative protection from " + id
                + " precursor; client={" + clientDiagnostics + "}; server={" + serverDiagnostics + "}"
        );
    }

    /**
     * Lets a newly equipped hostile player reach a source-faithful ready state before the modeled
     * approach begins. 26.1.2 applies equipment attribute modifiers from LivingEntity's normal
     * equipment-update tick, while Player then resets attack charge when it first observes the new
     * main-hand item. A real networked ServerPlayer receives that Player/LivingEntity phase from
     * ServerGamePacketListenerImpl.tick() -> ServerPlayer.doTick(). The embedded mock connection is
     * not part of the server's normal connection tick loop, so this helper supplies exactly that
     * missing player phase once per elapsed GameTest tick. It does not insert any delay between final
     * range entry and the hostile attack.
     */
    static void waitForReadyAttackState(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        AttackerHandle handle,
        String id
    ) {
        AttackReadyState last = null;
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            last = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer attacker = requireAttacker(server, handle);
                attacker.doTick();
                return new AttackReadyState(
                    attacker.getAttackStrengthScale(0.5f),
                    attacker.getCurrentItemAttackStrengthDelay(),
                    attacker.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    attacker.getMainHandItem().copy()
                );
            });
            if (last.attackStrength() >= 0.99f && last.attackDamage() > 1.0d) return;
            context.waitTick();
        }
        throw new AssertionError(
            "mock attacker never reached ready weapon state for " + id + "; last=" + last
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
        UUID victimId,
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
                requireVictim(server, victimId).getInventory().getSelectedSlot() == slot
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

    static ServerPlayer requireVictim(net.minecraft.server.MinecraftServer server, UUID victimId) {
        ServerPlayer player = server.getPlayerList().getPlayer(victimId);
        if (player == null) throw new AssertionError("gametest victim disappeared during burst sequence");
        return player;
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

    private record AttackReadyState(
        float attackStrength,
        float attackDelay,
        double attackDamage,
        ItemStack mainHand
    ) {
    }

    record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    record AttackerHandle(UUID playerId, int entityId) {
    }
}
