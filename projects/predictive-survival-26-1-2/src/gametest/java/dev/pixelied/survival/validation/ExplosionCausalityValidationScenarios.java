package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.planner.SurvivalAction;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proofs for explosion source causality in production planning. */
final class ExplosionCausalityValidationScenarios {
    private ExplosionCausalityValidationScenarios() {
    }

    static void validateAdjacentObservedCrystalsNeedOnlyOneProtection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 280d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 5f);
            victim.getInventory().setItem(2, new ItemStack(Items.TOTEM_OF_UNDYING));
            victim.containerMenu.broadcastChanges();
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            double x = center.getX() + 0.5d;
            double y = center.getY();
            EndCrystal near = new EndCrystal(level, x, y, center.getZ() + 2.5d);
            EndCrystal far = new EndCrystal(level, x, y, center.getZ() + 5.5d);
            level.addFreshEntity(near);
            level.addFreshEntity(far);

            return new Setup(
                victim.getUUID(),
                originalPosition,
                center,
                near.getId(),
                far.getId(),
                originals
            );
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.player.getInventory().getItem(2).is(Items.TOTEM_OF_UNDYING)
                && minecraft.level.getEntity(setup.nearCrystalId()) instanceof EndCrystal
                && minecraft.level.getEntity(setup.farCrystalId()) instanceof EndCrystal);

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Diagnostics diagnostics = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture(RescuePolicy.totemOnly());
                String nearId = "explosion:" + setup.nearCrystalId();
                String farId = "explosion:" + setup.farCrystalId();
                boolean sawNear = frame.actualTimeline().events().stream().anyMatch(event -> event.id().equals(nearId));
                boolean sawFar = frame.actualTimeline().events().stream().anyMatch(event -> event.id().equals(farId));
                long protectionCandidates = frame.candidates().stream()
                    .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                    .count();
                return new Diagnostics(
                    sawNear,
                    sawFar,
                    protectionCandidates,
                    frame.actualTimeline().events().stream()
                        .map(event -> event.id() + "=" + event.damage().rawDamage())
                        .toList()
                        .toString(),
                    frame.candidates().toString()
                );
            });

            if (!diagnostics.sawNear() || !diagnostics.sawFar()) {
                throw new AssertionError(
                    "production frame did not contain both observed crystal threats; actual=" + diagnostics.actualThreats()
                );
            }
            if (diagnostics.protectionCandidates() != 1L) {
                throw new AssertionError(
                    "adjacent observed crystals that destroy one another must require exactly one protection route; "
                        + "protectionCandidates=" + diagnostics.protectionCandidates()
                        + " actual=" + diagnostics.actualThreats()
                        + " candidates=" + diagnostics.candidates()
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim == null) return;
                ServerLevel level = (ServerLevel) victim.level();
                Entity near = level.getEntity(setup.nearCrystalId());
                Entity far = level.getEntity(setup.farCrystalId());
                if (near != null) near.discard();
                if (far != null) far.discard();
                restore(level, setup.originals());
                SurvivalValidationClientGameTest.reset(victim, 20f);
                victim.setNoGravity(false);
                victim.teleportTo(
                    setup.originalPosition().x,
                    setup.originalPosition().y,
                    setup.originalPosition().z
                );
            });
            context.waitTick();
        }
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 8; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return Map.copyOf(originals);
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        int nearCrystalId,
        int farCrystalId,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record Diagnostics(
        boolean sawNear,
        boolean sawFar,
        long protectionCandidates,
        String actualThreats,
        String candidates
    ) {
    }
}
