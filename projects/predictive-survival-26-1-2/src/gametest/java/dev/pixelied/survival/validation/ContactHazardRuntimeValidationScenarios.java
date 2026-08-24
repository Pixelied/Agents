package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class ContactHazardRuntimeValidationScenarios {
    private ContactHazardRuntimeValidationScenarios() {
    }

    static void validateMagmaContactReachesProductionRuntime(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            BlockPos floor = player.getOnPos();
            BlockState previous = player.level().getBlockState(floor);
            player.level().setBlockAndUpdate(floor, Blocks.MAGMA_BLOCK.defaultBlockState());
            return new Setup(floor, previous);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getBlockStateOn().is(Blocks.MAGMA_BLOCK));

            boolean predicted = context.computeOnClient(minecraft ->
                new MinecraftSurvivalRuntime(minecraft).capture().timeline().events().stream()
                    .anyMatch(event -> "minecraft:hot_floor".equals(event.damage().sourceKey()))
            );
            if (!predicted) {
                throw new AssertionError("production runtime omitted live magma contact threat");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                player.level().setBlockAndUpdate(setup.floor(), setup.previous());
            });
            context.waitTick();
        }
    }

    private record Setup(BlockPos floor, BlockState previous) {
    }
}
