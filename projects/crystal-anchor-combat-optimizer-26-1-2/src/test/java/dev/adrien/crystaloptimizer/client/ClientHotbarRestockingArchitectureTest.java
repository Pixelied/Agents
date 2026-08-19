package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHotbarRestockingArchitectureTest {
    @Test
    void v2StrategicTickRestocksOnlyOutsidePendingReactiveReservationsUsingVanillaSwap() throws IOException {
        Path coordinatorPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        );
        Path restockerPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/HotbarRestocker.java"
        );
        String coordinator = Files.readString(coordinatorPath);

        assertTrue(Files.exists(restockerPath), "live vanilla hotbar restocker must exist");
        String restocker = Files.readString(restockerPath);

        assertTrue(coordinator.contains("HotbarRestocker"));
        assertTrue(coordinator.contains("pendingItems.reservationCount() == 0"),
            "inventory mutation must not race an outstanding reactive item reservation");
        assertTrue(coordinator.contains("restocker.restockOne(self)"));
        int restock = coordinator.indexOf("restocker.restockOne(self)");
        int scanner = coordinator.indexOf("scanner.scan(", restock);
        int stop = coordinator.indexOf("return;", restock);
        assertTrue(restock >= 0 && stop > restock && scanner > stop,
            "a restock tick must stop before strategic scanning to avoid racing inventory state");

        assertTrue(restocker.contains("player.containerMenu != player.inventoryMenu"),
            "restocking must reject menus whose slot mapping is not the normal player inventory");
        assertTrue(restocker.contains("minecraft.screen != null"),
            "restocking must reject swaps while the user has an inventory/container screen open");
        assertTrue(restocker.contains("handleContainerInput("));
        assertTrue(restocker.contains("ContainerInput.SWAP"),
            "26.1.2 vanilla hotbar swap must be used instead of fabricated inventory packets");
        assertTrue(restocker.contains("sourceInventorySlot()"));
        assertTrue(restocker.contains("hotbarSlot()"));

        assertFalse(restocker.contains("CommitPhase"),
            "V2 restocking must not depend on the removed V1 commit state machine");
        assertFalse(restocker.contains("ServerboundContainerClickPacket"),
            "restocker must call the vanilla controller, not construct raw container packets");
        assertFalse(restocker.contains("getOffhandItem"),
            "Aura restocking must not mutate or reserve offhand; future AutoTotem owns it");
    }
}
