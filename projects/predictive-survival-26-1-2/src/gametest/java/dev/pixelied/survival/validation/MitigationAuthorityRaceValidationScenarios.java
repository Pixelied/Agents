package dev.pixelied.survival.validation;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Exact-runtime proof that client-only armor prediction cannot manufacture server mitigation. */
final class MitigationAuthorityRaceValidationScenarios {
    private static final int CHEST_INVENTORY_INDEX = 38;
    private static final double EPSILON = 1.0E-6d;

    private MitigationAuthorityRaceValidationScenarios() {
    }

    static void validateOptimisticLocalArmorDoesNotCountAsServerMitigation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        UUID victimId = singleplayer.getServer().computeOnServer(server -> {
            var victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(victim, 20f);
            victim.getInventory().clearContent();
            victim.getInventory().setSelectedSlot(0);
            victim.getInventory().setItem(0, new ItemStack(Items.STICK));
            victim.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            victim.containerMenu.broadcastChanges();
            return victim.getUUID();
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getItem(CHEST_INVENTORY_INDEX).isEmpty()
                && minecraft.player.getAttributeValue(Attributes.ARMOR) <= EPSILON
                && minecraft.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) <= EPSILON);

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Baseline baseline = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                return new Baseline(
                    frame.context().player().mitigation().armor(),
                    frame.context().player().mitigation().toughness(),
                    frame.context().player().mitigation().armorPieces().size()
                );
            });
            if (baseline.armor() > EPSILON || baseline.toughness() > EPSILON || baseline.armorPieces() != 0) {
                throw new AssertionError("mitigation authority baseline was not unarmored: " + baseline);
            }

            context.runOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client victim disappeared before armor race");
                // Deliberately mutate only LocalPlayer inventory. No game-mode/container click is
                // dispatched, so the server never receives an armor change from this test.
                minecraft.player.getInventory().setItem(
                    CHEST_INVENTORY_INDEX,
                    new ItemStack(Items.DIAMOND_CHESTPLATE)
                );
            });

            // Let vanilla's normal client LivingEntity equipment-update tick apply the locally
            // rendered chestplate attributes. The race is meaningful only after raw local armor is
            // genuinely non-zero rather than merely changing inventory bytes.
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getItem(CHEST_INVENTORY_INDEX).is(Items.DIAMOND_CHESTPLATE)
                && minecraft.player.getAttributeValue(Attributes.ARMOR) > EPSILON
                && minecraft.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) > EPSILON);

            ServerState serverState = singleplayer.getServer().computeOnServer(server -> {
                var victim = BurstSequenceValidationSupport.requireVictim(server, victimId);
                return new ServerState(
                    victim.getInventory().getItem(CHEST_INVENTORY_INDEX).isEmpty(),
                    victim.getAttributeValue(Attributes.ARMOR),
                    victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
                );
            });
            if (!serverState.chestEmpty()
                || serverState.armor() > EPSILON
                || serverState.toughness() > EPSILON) {
                throw new AssertionError("test contamination: server gained armor from client-only race: " + serverState);
            }

            RaceFrame race = context.computeOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client victim disappeared during armor race");
                double rawArmor = minecraft.player.getAttributeValue(Attributes.ARMOR);
                double rawToughness = minecraft.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                var frame = harness.runtime().capture();
                var mitigation = frame.context().player().mitigation();
                return new RaceFrame(
                    rawArmor,
                    rawToughness,
                    mitigation.armor(),
                    mitigation.toughness(),
                    mitigation.armorPieces().size()
                );
            });

            if (race.rawClientArmor() <= EPSILON || race.rawClientToughness() <= EPSILON) {
                throw new AssertionError("armor race failed to establish optimistic local mitigation: " + race);
            }
            if (race.authoritativeArmor() > EPSILON
                || race.authoritativeToughness() > EPSILON
                || race.authoritativeArmorPieces() != 0) {
                throw new AssertionError(
                    "runtime credited client-only armor before server authority: " + race
                );
            }

            ServerState serverAfterCapture = singleplayer.getServer().computeOnServer(server -> {
                var victim = BurstSequenceValidationSupport.requireVictim(server, victimId);
                return new ServerState(
                    victim.getInventory().getItem(CHEST_INVENTORY_INDEX).isEmpty(),
                    victim.getAttributeValue(Attributes.ARMOR),
                    victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
                );
            });
            if (!serverAfterCapture.chestEmpty()
                || serverAfterCapture.armor() > EPSILON
                || serverAfterCapture.toughness() > EPSILON) {
                throw new AssertionError("runtime capture contaminated server armor state: " + serverAfterCapture);
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                var victim = server.getPlayerList().getPlayer(victimId);
                if (victim != null) {
                    SurvivalValidationClientGameTest.reset(victim, 20f);
                    victim.getInventory().clearContent();
                    victim.getInventory().setSelectedSlot(0);
                    victim.containerMenu.broadcastChanges();
                }
            });
            context.runOnClient(minecraft -> {
                if (minecraft.player != null) {
                    minecraft.player.getInventory().setItem(CHEST_INVENTORY_INDEX, ItemStack.EMPTY);
                }
            });
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getItem(CHEST_INVENTORY_INDEX).isEmpty()
                && minecraft.player.getAttributeValue(Attributes.ARMOR) <= EPSILON
                && minecraft.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) <= EPSILON);
        }
    }

    private record Baseline(float armor, float toughness, int armorPieces) {
    }

    private record ServerState(boolean chestEmpty, double armor, double toughness) {
    }

    private record RaceFrame(
        double rawClientArmor,
        double rawClientToughness,
        float authoritativeArmor,
        float authoritativeToughness,
        int authoritativeArmorPieces
    ) {
    }
}
