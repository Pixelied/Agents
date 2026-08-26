package dev.pixelied.survival.validation;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Dedicated runner for the approved instant-burst exact-runtime validation plan. */
public final class InstantBurstValidationClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(minecraft -> minecraft.player != null && minecraft.level != null);
            waitForServerClientLoaded(context, singleplayer);
            ExplosionExposureDifferentialValidationScenarios.validateExactCollisionShapeExposure(context, singleplayer);
            CrystalBurstSequenceValidationScenarios.validatePrecursorThenZeroDelayPlaceBreak(context, singleplayer);
            BedAnchorBurstSequenceValidationScenarios.validateUnchargedAnchorChargeThenUseWithoutObservationGap(
                context,
                singleplayer
            );
            ChargedAnchorBurstSequenceValidationScenarios.validateChargedAnchorImmediateUse(context, singleplayer);
            BedAnchorBurstSequenceValidationScenarios.validateExplosiveBedPlaceThenUseWithoutObservationGap(
                context,
                singleplayer
            );
            TntMinecartCollisionValidationScenarios.validateForecastCollisionArmsBeforeUnprimedBurst(
                context,
                singleplayer
            );
            TntMinecartBurningArrowValidationScenarios.validateBurningArrowArmsBeforeUnprimedBurst(
                context,
                singleplayer
            );
            NetworkAgedFuseValidationScenarios.validateDelayedTntObservationContainsAuthoritativeDetonation(
                context,
                singleplayer
            );
            MeleeBurstSequenceValidationScenarios.validatePlayerCrossesRangeAndAttacksAtFirstLegalTick(
                context,
                singleplayer
            );
            MeleeBurstSequenceValidationScenarios.validateMaceCrossesRangeAndSmashesAtFirstLegalTick(
                context,
                singleplayer
            );
            SpearBurstSequenceValidationScenarios.validatePiercingSpearCrossesRayAndStabsAtFirstLegalTick(
                context,
                singleplayer
            );
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
        throw new AssertionError("server player did not report client-loaded readiness before instant-burst validation");
    }
}
