package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.damage.ArmorPieceSnapshot;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.execution.MinecraftServerStateEvidence;
import dev.pixelied.survival.execution.ServerStateEvidenceSnapshot;
import dev.pixelied.survival.inventory.MinecraftInventorySnapshotFactory;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.inventory.SurvivalItemRoutePlanner;
import dev.pixelied.survival.planner.SurvivalAction;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;

final class SurvivalItemRoutingValidationScenarios {
    private static final RescuePolicy POLICY = RescuePolicy.smartDefaults();

    private SurvivalItemRoutingValidationScenarios() {
    }

    static void validateHotbarAndContainerEquipmentRoutes(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validateHotbarSelectUseAndRestore(context, singleplayer);
        validateContainerSwapUseAndInverseRestore(context, singleplayer);
    }

    private static void validateHotbarSelectUseAndRestore(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        final int sourceIndex = 2;
        prepareEquipmentRoute(singleplayer, sourceIndex);
        waitForEquipmentInventory(context, sourceIndex);

        EquipmentHarness harness = context.computeOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable for hotbar route validation");
            MinecraftInventorySnapshotFactory snapshots = new MinecraftInventorySnapshotFactory();
            var inventory = snapshots.captureInventory(minecraft.player);
            var menu = snapshots.captureMenu(minecraft.player);
            var source = inventory.slot(sourceIndex).orElseThrow();
            SurvivalItemRoute route = new SurvivalItemRoutePlanner()
                .route(inventory, menu, source, true, true)
                .orElseThrow(() -> new AssertionError("no hotbar survival-item route"));
            if (!(route instanceof SurvivalItemRoute.HotbarSelect hotbar) || hotbar.hotbarIndex() != sourceIndex) {
                throw new AssertionError("expected HotbarSelect route for source " + sourceIndex + ", got " + route);
            }
            return beginEquipmentAction(minecraft, source, route);
        });

        waitForServerSelection(context, singleplayer, sourceIndex, "routed equipment hotbar selection");
        observeUntilConfirmed(context, singleplayer, harness, sourceIndex, "routed hotbar equipment use");
        assertServerEquipped(singleplayer, "hotbar routed equipment");

        maintainUntil(context, singleplayer, harness, () -> serverState(singleplayer, sourceIndex).selectedSlot() == 0,
            "hotbar route hand restoration");
        ServerState restored = serverState(singleplayer, sourceIndex);
        if (!restored.selectedStack().is(Items.DIAMOND_SWORD)
            || !restored.chestStack().is(Items.NETHERITE_CHESTPLATE)
            || !restored.sourceStack().isEmpty()) {
            throw new AssertionError("hotbar route restoration corrupted authoritative inventory/equipment: " + restored);
        }

        cleanup(singleplayer);
        context.waitTick();
    }

    private static void validateContainerSwapUseAndInverseRestore(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        final int sourceIndex = 10;
        prepareEquipmentRoute(singleplayer, sourceIndex);
        waitForEquipmentInventory(context, sourceIndex);

        EquipmentHarness harness = context.computeOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable for container route validation");
            MinecraftInventorySnapshotFactory snapshots = new MinecraftInventorySnapshotFactory();
            var inventory = snapshots.captureInventory(minecraft.player);
            var menu = snapshots.captureMenu(minecraft.player);
            var source = inventory.slot(sourceIndex).orElseThrow();
            SurvivalItemRoute route = new SurvivalItemRoutePlanner()
                .route(inventory, menu, source, true, true)
                .orElseThrow(() -> new AssertionError("no container survival-item route"));
            if (!(route instanceof SurvivalItemRoute.ContainerSwap swap)
                || swap.sourceInventoryIndex() != sourceIndex
                || swap.destinationInventoryIndex() != 0
                || swap.button() != 0) {
                throw new AssertionError("expected mainhand ContainerSwap route for source " + sourceIndex + ", got " + route);
            }
            return beginEquipmentAction(minecraft, source, route);
        });

        waitForServerSwap(context, singleplayer, sourceIndex);
        observeUntilConfirmed(context, singleplayer, harness, sourceIndex, "routed container equipment use");
        assertServerEquipped(singleplayer, "container routed equipment");

        maintainUntil(context, singleplayer, harness, () -> {
            ServerState state = serverState(singleplayer, sourceIndex);
            return state.selectedStack().is(Items.DIAMOND_SWORD) && state.sourceStack().isEmpty();
        }, "container route inverse restoration");

        ServerState restored = serverState(singleplayer, sourceIndex);
        if (restored.selectedSlot() != 0
            || !restored.selectedStack().is(Items.DIAMOND_SWORD)
            || !restored.sourceStack().isEmpty()
            || !restored.chestStack().is(Items.NETHERITE_CHESTPLATE)) {
            throw new AssertionError("container inverse restoration corrupted authoritative inventory/equipment: " + restored);
        }

        cleanup(singleplayer);
        context.waitTick();
    }

    private static EquipmentHarness beginEquipmentAction(
        net.minecraft.client.Minecraft minecraft,
        dev.pixelied.survival.inventory.InventorySlotSnapshot source,
        SurvivalItemRoute route
    ) {
        var equippable = source.equippable()
            .orElseThrow(() -> new AssertionError("netherite chestplate did not expose equippable capability"));
        MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
        SurvivalEngine.EngineFrame frame = runtime.capture(POLICY);
        SurvivalAction.SwapEquipment action = new SurvivalAction.SwapEquipment(
            frame.context().player().mitigation(),
            Map.of(EquipmentSlot.CHEST.getName(), "minecraft:netherite_chestplate"),
            route.requiredServerTicks() + 1,
            true,
            true,
            1d,
            0,
            2,
            Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), source.stackKey(), source.componentFingerprint(), Optional.of(route)
            )),
            Optional.of(equippable.armorPiece())
        );
        MitigationSnapshot expectedMitigation = action.apply(frame.context().player()).mitigation();
        ExecutionStatus initial = runtime.begin(action, frame);
        if (!(initial instanceof ExecutionStatus.WaitingForServer)) {
            throw new AssertionError("routed equipment action did not begin asynchronously: " + initial);
        }
        return new EquipmentHarness(runtime, action, expectedMitigation, equippable.armorPiece());
    }

    private static void observeUntilConfirmed(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        EquipmentHarness harness,
        int sourceIndex,
        String phase
    ) {
        ExecutionStatus last = null;
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            ExecutionStatus status = context.computeOnClient(minecraft -> {
                SurvivalEngine.EngineFrame frame = harness.runtime().capture(POLICY);
                return harness.runtime().observe(harness.action(), frame);
            });
            last = status;
            if (status instanceof ExecutionStatus.Confirmed) return;
            if (status instanceof ExecutionStatus.Failed failed) {
                throw new AssertionError(
                    phase + " failed: " + failed.reason()
                        + "; server=" + serverState(singleplayer, sourceIndex)
                        + "; client=" + clientState(context, sourceIndex)
                        + "; runtime=" + clientRuntimeState(context, harness)
                );
            }
            context.waitTick();
        }
        throw new AssertionError(
            phase + " was never authoritatively confirmed; last=" + last
                + "; server=" + serverState(singleplayer, sourceIndex)
                + "; client=" + clientState(context, sourceIndex)
                + "; runtime=" + clientRuntimeState(context, harness)
        );
    }

    private static void maintainUntil(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        EquipmentHarness harness,
        java.util.function.BooleanSupplier done,
        String phase
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            if (done.getAsBoolean()) return;
            context.runOnClient(minecraft -> {
                SurvivalEngine.EngineFrame frame = harness.runtime().capture(POLICY);
                harness.runtime().maintainRestoration(frame, true, false, false);
            });
            context.waitTick();
        }
        throw new AssertionError(phase + " did not complete before timeout");
    }

    private static void prepareEquipmentRoute(TestSingleplayerContext singleplayer, int sourceIndex) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            clearInventory(player);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            player.getInventory().setItem(sourceIndex, new ItemStack(Items.NETHERITE_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
    }

    private static void waitForEquipmentInventory(ClientGameTestContext context, int sourceIndex) {
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getInventory().getSelectedSlot() == 0
            && minecraft.player.getInventory().getItem(0).is(Items.DIAMOND_SWORD)
            && minecraft.player.getInventory().getItem(sourceIndex).is(Items.NETHERITE_CHESTPLATE)
            && minecraft.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
    }

    private static void waitForServerSelection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int expected,
        String phase
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            if (serverState(singleplayer, expected).selectedSlot() == expected) return;
            context.waitTick();
        }
        throw new AssertionError(phase + " never reached server-selected slot " + expected);
    }

    private static void waitForServerSwap(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int sourceIndex
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            ServerState state = serverState(singleplayer, sourceIndex);
            if (state.selectedStack().is(Items.NETHERITE_CHESTPLATE)
                && state.sourceStack().is(Items.DIAMOND_SWORD)) {
                return;
            }
            context.waitTick();
        }
        throw new AssertionError("server never confirmed container survival-item swap from " + sourceIndex);
    }

    private static void assertServerEquipped(TestSingleplayerContext singleplayer, String phase) {
        ServerState state = serverState(singleplayer, 10);
        if (!state.chestStack().is(Items.NETHERITE_CHESTPLATE)) {
            throw new AssertionError(phase + " did not equip netherite chestplate on authoritative server: " + state);
        }
    }

    private static ServerState serverState(TestSingleplayerContext singleplayer, int sourceIndex) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            int selected = player.getInventory().getSelectedSlot();
            return new ServerState(
                selected,
                player.getInventory().getItem(selected).copy(),
                player.getInventory().getItem(sourceIndex).copy(),
                player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                player.containerMenu.getStateId()
            );
        });
    }

    private static ClientState clientState(ClientGameTestContext context, int sourceIndex) {
        return context.computeOnClient(minecraft -> {
            if (minecraft.player == null) return new ClientState(-1, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, -1);
            var player = minecraft.player;
            int selected = player.getInventory().getSelectedSlot();
            return new ClientState(
                selected,
                player.getInventory().getItem(selected).copy(),
                player.getInventory().getItem(sourceIndex).copy(),
                player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                player.containerMenu.getStateId()
            );
        });
    }

    private static ClientRuntimeState clientRuntimeState(
        ClientGameTestContext context,
        EquipmentHarness harness
    ) {
        return context.computeOnClient(minecraft -> {
            SurvivalEngine.EngineFrame frame = harness.runtime().capture(POLICY);
            return new ClientRuntimeState(
                harness.expectedMitigation(),
                frame.context().player().mitigation(),
                harness.plannedPiece(),
                frame.context().player().equipmentItemKeys(),
                MinecraftServerStateEvidence.snapshot()
            );
        });
    }

    private static void cleanup(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            clearInventory(player);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.getInventory().setSelectedSlot(0);
            player.containerMenu.broadcastChanges();
        });
    }

    private static void clearInventory(ServerPlayer player) {
        for (int index = 0; index <= 35; index++) {
            player.getInventory().setItem(index, ItemStack.EMPTY);
        }
        player.getInventory().setItem(40, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
    }

    private record EquipmentHarness(
        MinecraftSurvivalRuntime runtime,
        SurvivalAction.SwapEquipment action,
        MitigationSnapshot expectedMitigation,
        ArmorPieceSnapshot plannedPiece
    ) {
    }

    private record ServerState(
        int selectedSlot,
        ItemStack selectedStack,
        ItemStack sourceStack,
        ItemStack chestStack,
        int menuStateId
    ) {
    }

    private record ClientState(
        int selectedSlot,
        ItemStack selectedStack,
        ItemStack sourceStack,
        ItemStack chestStack,
        int menuStateId
    ) {
    }

    private record ClientRuntimeState(
        MitigationSnapshot expectedMitigation,
        MitigationSnapshot observedMitigation,
        ArmorPieceSnapshot plannedPiece,
        Map<String, String> equipmentItemKeys,
        ServerStateEvidenceSnapshot evidence
    ) {
    }
}
