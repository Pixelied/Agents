package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class OffhandStackCaptureArchitectureTest {
    @Test
    void plannerAndLiveDispatcherCaptureExactOffhandStackCount() throws Exception {
        String snapshotBuilder = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/world/ClientCombatSnapshotBuilder.java"
        ));
        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));

        assertConstructorCarriesOffhandCount(snapshotBuilder, "strategic snapshot");
        assertConstructorCarriesOffhandCount(dispatcher, "live dispatcher");
    }

    private static void assertConstructorCarriesOffhandCount(String source, String label) {
        int constructor = source.indexOf("return new InventoryState(");
        int offhandItem = source.indexOf("offhand.isEmpty()", constructor);
        int offhandCount = source.indexOf("offhand.getCount()", offhandItem);
        int constructorEnd = source.indexOf(");", constructor);
        assertTrue(
            constructor >= 0
                && offhandItem > constructor
                && offhandCount > offhandItem
                && offhandCount < constructorEnd,
            label + " must pass the real offhand stack count into InventoryState"
        );
    }
}
