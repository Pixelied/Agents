package dev.pixelied.survival.validation;

import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.planner.SurvivalAction;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Exact-runtime optimistic offhand authority race. */
final class ProtectionOffhandAuthorityRaceValidationScenarios {
    private static final double POSITION_EPSILON = 0.05d;

    private ProtectionOffhandAuthorityRaceValidationScenarios() {
    }

    static void validateOptimisticLocalOffhandDoesNotCountAsServerProtection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 original = victim.position();
            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(victim.getX(), 232d, victim.getZ());
            victim.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            victim.containerMenu.broadcastChanges();
            return new Setup(victim.getUUID(), original, victim.getX(), victim.getZ());
        });

        int crystalId = -1;
        try {
            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getY() - 232d) <= POSITION_EPSILON
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(0).is(Items.STICK)
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.player.getOffhandItem().isEmpty());

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Baseline baseline = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                return new Baseline(
                    minecraft.player.getInventory().getSelectedSlot(),
                    frame.context().player().deathProtection().anyHandAvailable()
                );
            });
            if (baseline.clientSelectedSlot() != 0 || baseline.protectionCredited()) {
                throw new AssertionError("offhand authority baseline was not synchronized and unprotected: " + baseline);
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
                if (minecraft.player == null) throw new AssertionError("client victim disappeared during offhand race");
                if (minecraft.player.getInventory().getSelectedSlot() != 0) {
                    throw new AssertionError("client selected slot changed before offhand race");
                }

                // Simulate an optimistic container prediction only on LocalPlayer. No serverbound
                // click is sent and the local mutation is reverted before this callback returns.
                minecraft.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                try {
                    boolean locallyRenderedTotem = minecraft.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
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
                        frame.actualTimeline().events().stream()
                            .map(event -> event.kind() + ":" + event.id()).toList().toString(),
                        frame.candidates().toString()
                    );
                } finally {
                    minecraft.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                }
            });

            if (!race.locallyRenderedTotem()) {
                throw new AssertionError("offhand authority race failed to create optimistic local Totem state");
            }
            if (!race.crystalThreatVisible()) {
                throw new AssertionError("offhand authority race did not contain live crystal threat: " + race.actualThreats());
            }
            if (race.protectionCredited()) {
                throw new AssertionError(
                    "runtime credited optimistic local offhand Totem without inbound server evidence; actual="
                        + race.actualThreats() + " candidates=" + race.candidates()
                );
            }
            if (race.hand() != SurvivalAction.Hand.MAIN_HAND
                || !(race.route() instanceof DeathProtectionRoute.HotbarSelect select)
                || select.hotbarIndex() != 1) {
                throw new AssertionError("runtime did not keep the real hotbar Totem route during offhand optimism: " + race);
            }

            boolean serverStillUnprotected = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = requireVictim(server, setup.victimId());
                return victim.getOffhandItem().isEmpty() && victim.getInventory().getSelectedSlot() == 0;
            });
            if (!serverStillUnprotected) {
                throw new AssertionError("test contamination: server hand changed during client-only offhand mutation");
            }
        } finally {
            int cleanupCrystalId = crystalId;
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim == null) return;
                Entity crystal = cleanupCrystalId < 0 ? null : ((ServerLevel) victim.level()).getEntity(cleanupCrystalId);
                if (crystal != null) crystal.discard();
                SurvivalValidationClientGameTest.reset(victim, 20f);
                victim.setNoGravity(false);
                victim.getInventory().clearContent();
                victim.getInventory().setSelectedSlot(0);
                victim.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                victim.teleportTo(setup.originalPosition().x, setup.originalPosition().y, setup.originalPosition().z);
                victim.containerMenu.broadcastChanges();
            });
            context.runOnClient(minecraft -> {
                if (minecraft.player != null) {
                    minecraft.player.getInventory().setSelectedSlot(0);
                    minecraft.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                }
            });
            context.waitTick();
        }
    }

    private static ServerPlayer requireVictim(net.minecraft.server.MinecraftServer server, UUID victimId) {
        ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
        if (victim == null) throw new AssertionError("victim disappeared during offhand authority race");
        return victim;
    }

    private record Setup(UUID victimId, Vec3 originalPosition, double x, double z) {
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
