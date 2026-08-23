package dev.pixelied.survival.validation;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Exact-runtime validation for the new generic survival-item select/swap/use routes. */
public final class SurvivalItemRoutingClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(minecraft -> minecraft.player != null && minecraft.level != null);
            waitForServerClientLoaded(context, singleplayer);
            SurvivalItemRoutingValidationScenarios.validateHotbarAndContainerEquipmentRoutes(context, singleplayer);
        }
    }

    private static void waitForServerClientLoaded(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean loaded = singleplayer.getServer().computeOnServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                return players.size() == 1 && players.getFirst().connection.hasClientLoaded();
            });
            if (loaded) return;
            context.waitTick();
        }
        throw new AssertionError("server player did not report client-loaded readiness before routed-item validation timeout");
    }
}
