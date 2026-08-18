package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHotbarRestockingArchitectureTest {
    @Test
    void liveRuntimeRestocksOnlyOutsideTimingCriticalExecutionUsingVanillaSwap() throws IOException {
        Path runtimePath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java"
        );
        Path restockerPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/HotbarRestocker.java"
        );
        String runtime = Files.readString(runtimePath);

        assertTrue(Files.exists(restockerPath), "live vanilla hotbar restocker must exist");
        String restocker = Files.readString(restockerPath);

        assertTrue(runtime.contains("HotbarRestocker"));
        assertTrue(runtime.contains("engine.phase() == CommitPhase.NORMAL"),
            "inventory mutation must be forbidden during committed/reconciling execution");
        assertTrue(runtime.contains("if (restocker.restockOne(self))"));
        assertTrue(runtime.contains("return;"),
            "a restock tick must stop before snapshot/planning to avoid racing predicted inventory state");

        assertTrue(restocker.contains("player.containerMenu == player.inventoryMenu"),
            "restocking must only use the normal player inventory menu slot mapping");
        assertTrue(restocker.contains("minecraft.screen == null"),
            "restocking must not fight a user who has an inventory/container screen open");
        assertTrue(restocker.contains("handleContainerInput("));
        assertTrue(restocker.contains("ContainerInput.SWAP"),
            "26.1.2 vanilla hotbar swap must be used instead of fabricated inventory packets");
        assertTrue(restocker.contains("decision.sourceInventorySlot()"));
        assertTrue(restocker.contains("decision.hotbarSlot()"));

        assertFalse(restocker.contains("ServerboundContainerClickPacket"),
            "restocker must call the vanilla controller, not construct raw container packets");
        assertFalse(restocker.contains("getOffhandItem"),
            "Aura restocking must not mutate or reserve offhand; future AutoTotem owns it");
    }
}
