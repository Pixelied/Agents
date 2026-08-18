package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientInventoryCaptureArchitectureTest {
    @Test
    void liveSnapshotPreservesExactHotbarStackCounts() throws IOException {
        Path builderPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/world/ClientCombatSnapshotBuilder.java"
        );
        String source = Files.readString(builderPath);

        assertTrue(source.contains("LinkedHashMap<Integer, Integer> hotbarCounts"),
            "live inventory capture must keep a quantity map for hotbar slots");
        assertTrue(source.contains("hotbarCounts.put(slot, stack.getCount())"),
            "each real hotbar ItemStack count must be copied into the immutable snapshot");

        int constructor = source.indexOf("return new InventoryState(");
        int itemMap = source.indexOf("hotbar,", constructor);
        int countMap = source.indexOf("hotbarCounts,", itemMap);
        int offhand = source.indexOf("offhand.isEmpty()", countMap);
        assertTrue(constructor >= 0 && itemMap > constructor && countMap > itemMap && offhand > countMap,
            "ClientCombatSnapshotBuilder must call the exact-stack InventoryState constructor");
    }
}
