package dev.pixelied.survival.validation;

import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.planner.SurvivalAction;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

/** Exact-runtime authority races where client-visible hands disagree with feasible server hands. */
final class ProtectionAuthorityRaceValidationScenarios {
    private static final double POSITION_EPSILON = 0.05d;

    private ProtectionAuthorityRaceValidationScenarios() {
    }

    static void validateOptimisticLocalHotbarDoesNotCountAsServerProtection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 original = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 232d, victim.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center);
            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
            victim.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            victim.containerMenu.broadcastChanges();
            return new Setup(victim.getUUID(), original, center, originals);
        });

        int crystalId = -1;
        try {
            waitForClientBaseline(context, setup.center());
            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);

            Baseline baseline = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                return new Baseline(
                    minecraft.player.getInventory().getSelectedSlot(),
                    frame.context().player().deathProtection().anyHandAvailable()
                );
            });
            if (baseline.clientSelectedSlot() != 0 || baseline.protectionCredited()) {
                throw new AssertionError("authority race baseline was not synchronized and unprotected: " + baseline);
            }
            int serverBaselineSlot = serverSelectedSlot(singleplayer, setup.victimId());
            if (serverBaselineSlot != 0) {
                throw new AssertionError("authority race server baseline selected slot " + serverBaselineSlot + " instead of 0");
            }

            crystalId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                EndCrystal crystal = new EndCrystal(level, victim.getX(), victim.getY() + 0.9d, victim.getZ() + 2.5d);
                level.addFreshEntity(crystal);
                return crystal.getId();
            });
            int finalCrystalId = crystalId;
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(finalCrystalId) instanceof EndCrystal);

            RaceFrame race = context.computeOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client victim disappeared during authority race");
                if (minecraft.player.getInventory().getSelectedSlot() != 0) {
                    throw new AssertionError("client baseline slot changed before authority race");
                }

                // Deliberately mutate only LocalPlayer. No dispatcher/game-mode call is made, and the
                // slot is restored before this client callback returns, so vanilla cannot send the
                // selection on a later client tick and accidentally make the test pass.
                minecraft.player.getInventory().setSelectedSlot(1);
                try {
                    boolean locallyRenderedTotem = minecraft.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
                    var frame = harness.runtime().capture();
                    SurvivalAction.EquipDeathProtection equip = frame.candidates().stream()
                        .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                        .map(SurvivalAction.EquipDeathProtection.class::cast)
                        .findFirst()
                        .orElse(null);
                    DeathProtectionRoute route = equip == null
                        ? null
                        : equip.sourceItem().map(SurvivalAction.DeathProtectionSourceRef::route).orElse(null);
                    boolean crystalThreatVisible = frame.actualTimeline().events().stream()
                        .anyMatch(event -> event.id().equals("explosion:" + finalCrystalId));
                    return new RaceFrame(
                        locallyRenderedTotem,
                        frame.context().player().deathProtection().anyHandAvailable(),
                        crystalThreatVisible,
                        equip == null ? null : equip.hand(),
                        route,
                        frame.actualTimeline().events().stream().map(event -> event.kind() + ":" + event.id()).toList().toString(),
                        frame.candidates().toString()
                    );
                } finally {
                    minecraft.player.getInventory().setSelectedSlot(0);
                }
            });

            if (!race.locallyRenderedTotem()) {
                throw new AssertionError("authority race failed to create the optimistic client-visible Totem hand");
            }
            if (!race.crystalThreatVisible()) {
                throw new AssertionError("authority race frame did not contain the live crystal threat: " + race.actualThreats());
            }
            if (race.protectionCredited()) {
                throw new AssertionError(
                    "runtime credited the optimistic client Totem before server authority; actual="
                        + race.actualThreats() + " candidates=" + race.candidates()
                );
            }
            if (race.hand() != SurvivalAction.Hand.MAIN_HAND
                || !(race.route() instanceof DeathProtectionRoute.HotbarSelect select)
                || select.hotbarIndex() != 1) {
                throw new AssertionError(
                    "runtime did not retain a corrective hotbar Totem route while local UI was optimistic; race=" + race
                );
            }
            int serverAfterCapture = serverSelectedSlot(singleplayer, setup.victimId());
            if (serverAfterCapture != 0) {
                throw new AssertionError(
                    "test contamination: server selected slot changed without a dispatched selection; slot=" + serverAfterCapture
                );
            }
        } finally {
            int cleanupCrystalId = crystalId;
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel) victim.level();
                    Entity crystal = cleanupCrystalId < 0 ? null : level.getEntity(cleanupCrystalId);
                    if (crystal != null) crystal.discard();
                    restore(level, setup.originals());
                    SurvivalValidationClientGameTest.reset(victim, 20f);
                    victim.setNoGravity(false);
                    victim.getInventory().clearContent();
                    victim.getInventory().setSelectedSlot(0);
                    victim.teleportTo(
                        setup.originalPosition().x,
                        setup.originalPosition().y,
                        setup.originalPosition().z
                    );
                    victim.containerMenu.broadcastChanges();
                }
            });
            context.runOnClient(minecraft -> {
                if (minecraft.player != null) minecraft.player.getInventory().setSelectedSlot(0);
            });
            context.waitTick();
        }
    }

    private static void waitForClientBaseline(ClientGameTestContext context, BlockPos center) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - (center.getX() + 0.5d)) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - center.getY()) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - (center.getZ() + 0.5d)) <= POSITION_EPSILON
            && minecraft.player.getInventory().getSelectedSlot() == 0
            && minecraft.player.getInventory().getItem(0).is(Items.STICK)
            && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
            && minecraft.player.getOffhandItem().isEmpty());
    }

    private static int serverSelectedSlot(TestSingleplayerContext singleplayer, UUID victimId) {
        return singleplayer.getServer().computeOnServer(server -> requireVictim(server, victimId).getInventory().getSelectedSlot());
    }

    private static ServerPlayer requireVictim(net.minecraft.server.MinecraftServer server, UUID victimId) {
        ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
        if (victim == null) throw new AssertionError("victim disappeared during protection authority race");
        return victim;
    }

    private static Map<BlockPos, BlockState> prepareArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = center.getY() - 1; y <= center.getY() + 3; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, y == center.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
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

    private record Setup(UUID victimId, Vec3 originalPosition, BlockPos center, Map<BlockPos, BlockState> originals) {
    }

    private record Baseline(int clientSelectedSlot, boolean protectionCredited) {
    }

    private record RaceFrame(
        boolean locallyRenderedTotem,
        boolean protectionCredited,
        boolean crystalThreatVisible,
        SurvivalAction.Hand hand,
        DeathProtectionRoute route,
        String actualThreats,
        String candidates
    ) {
    }
}
