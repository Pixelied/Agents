package dev.pixelied.survival.validation;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;

/** Runs the machine-readable damage gauntlet independently of production client startup. */
public final class DamageGauntletClientGameTest implements FabricClientGameTest {
    private static final String OUTPUT_PREFIX = "PREDICTIVE_SURVIVAL_DAMAGE_GAUNTLET ";

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(minecraft -> minecraft.player != null && minecraft.level != null);
            waitForServerClientLoaded(context, singleplayer);

            List<DamageGauntletScenarios.GauntletResult> results =
                DamageGauntletScenarios.existingCoveredSubset(context, singleplayer);
            if (results.isEmpty()) throw new AssertionError("damage gauntlet produced no results");

            for (DamageGauntletScenarios.GauntletResult result : results) {
                DamageGauntletScenarios.assertResult(result);
                System.out.println(OUTPUT_PREFIX + result.toJsonLine());
            }
        }
    }

    private static void waitForServerClientLoaded(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean loaded = singleplayer.getServer().computeOnServer(server -> {
                var players = server.getPlayerList().getPlayers();
                return players.size() == 1 && players.getFirst().connection.hasClientLoaded();
            });
            if (loaded) return;
            context.waitTick();
        }
        throw new AssertionError("damage gauntlet server player did not report client-loaded readiness");
    }
}
