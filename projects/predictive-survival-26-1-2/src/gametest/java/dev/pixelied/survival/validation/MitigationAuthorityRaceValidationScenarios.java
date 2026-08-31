package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.UUID;

/** Exact-runtime proof that client-only armor prediction cannot manufacture server mitigation. */
final class MitigationAuthorityRaceValidationScenarios {
    private static final int CHEST_INVENTORY_INDEX = 38;
    private static final int PROTECTION_LEVEL = 4;
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
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client victim/level disappeared before armor race");
                }
                // Deliberately mutate only LocalPlayer's shared EntityEquipment. No game-mode or
                // container-click path is dispatched, so the integrated server receives nothing.
                // Client LivingEntity does not run the server-only attribute recomputation pass,
                // but MinecraftEquipmentAdapter still observes this equipped stack and its visible
                // Protection enchantment. That is the actual optimistic mitigation input we need
                // the runtime authority layer to reject.
                ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
                var protection = minecraft.level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION);
                chestplate.enchant(protection, PROTECTION_LEVEL);
                minecraft.player.getInventory().setItem(CHEST_INVENTORY_INDEX, chestplate);
            });

            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getItem(CHEST_INVENTORY_INDEX).is(Items.DIAMOND_CHESTPLATE));

            RawClientMitigation rawClient = context.computeOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client victim disappeared during raw armor snapshot");
                var mitigation = new MinecraftSnapshotFactory().capture(minecraft.player).mitigation();
                int protection = mitigation.armorPieces().stream()
                    .mapToInt(piece -> piece.protectionEnchantments().protection())
                    .sum();
                return new RawClientMitigation(
                    minecraft.player.getAttributeValue(Attributes.ARMOR),
                    minecraft.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                    mitigation.armorPieces().size(),
                    protection
                );
            });
            if (rawClient.armorPieces() != 1 || rawClient.protectionLevel() != PROTECTION_LEVEL) {
                throw new AssertionError(
                    "armor race failed to establish client-only enchanted mitigation input: " + rawClient
                );
            }

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
                var frame = harness.runtime().capture();
                var mitigation = frame.context().player().mitigation();
                int protection = mitigation.armorPieces().stream()
                    .mapToInt(piece -> piece.protectionEnchantments().protection())
                    .sum();
                return new RaceFrame(
                    mitigation.armor(),
                    mitigation.toughness(),
                    mitigation.armorPieces().size(),
                    protection
                );
            });

            if (race.authoritativeArmor() > EPSILON
                || race.authoritativeToughness() > EPSILON
                || race.authoritativeArmorPieces() != 0
                || race.authoritativeProtectionLevel() != 0) {
                throw new AssertionError(
                    "runtime credited client-only enchanted armor before server authority; raw="
                        + rawClient + " authoritative=" + race
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
                && minecraft.player.getInventory().getItem(CHEST_INVENTORY_INDEX).isEmpty());
        }
    }

    private record Baseline(float armor, float toughness, int armorPieces) {
    }

    private record RawClientMitigation(
        double rawArmorAttribute,
        double rawToughnessAttribute,
        int armorPieces,
        int protectionLevel
    ) {
    }

    private record ServerState(boolean chestEmpty, double armor, double toughness) {
    }

    private record RaceFrame(
        float authoritativeArmor,
        float authoritativeToughness,
        int authoritativeArmorPieces,
        int authoritativeProtectionLevel
    ) {
    }
}
