package dev.pixelied.survival.validation;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/** Exact-runtime validation for synchronized 26.1.2 KINETIC_WEAPON spear state. */
final class KineticSpearValidationScenarios {
    private static final double POSITION_EPSILON = 0.05d;

    private KineticSpearValidationScenarios() {
    }

    static void validateSynchronizedKineticMetadataReachesProductionFrame(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 originalPosition = victim.position();
            BurstSequenceValidationSupport.prepareVictim(victim, 20f);
            Vec3 victimPosition = new Vec3(originalPosition.x, 322d, originalPosition.z);
            victim.teleportTo(victimPosition.x, victimPosition.y, victimPosition.z);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SPEAR));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setKnownMovement(Vec3.ZERO);
            attacker.setXRot(0f);
            attacker.setYRot(0f);
            attacker.setYHeadRot(0f);

            Vec3 attackerPosition = victimPosition.add(0d, 0d, 6d);
            attacker.teleportTo(attackerPosition.x, attackerPosition.y, attackerPosition.z);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
            victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));

            attacker.startUsingItem(InteractionHand.MAIN_HAND);
            attacker.doTick();
            if (attacker.getTicksUsingItem() != 1) {
                throw new AssertionError(
                    "kinetic spear fixture did not reach exactly one authoritative use tick; ticks="
                        + attacker.getTicksUsingItem()
                );
            }
            var values = attacker.getEntityData().getNonDefaultValues();
            if (values == null) {
                throw new AssertionError("kinetic spear use did not dirty synchronized living-entity state");
            }
            victim.connection.send(new ClientboundSetEntityDataPacket(attacker.getId(), values));

            return new Setup(victim.getUUID(), originalPosition, attackerPosition, handle);
        });

        try {
            context.waitFor(minecraft -> {
                if (minecraft.level == null) return false;
                Entity entity = minecraft.level.getEntity(setup.attacker().entityId());
                if (!(entity instanceof net.minecraft.world.entity.player.Player remote)) return false;
                return remote.getMainHandItem().is(Items.NETHERITE_SPEAR)
                    && remote.getMainHandItem().has(DataComponents.KINETIC_WEAPON)
                    && remote.isUsingItem()
                    && remote.getUsedItemHand() == InteractionHand.MAIN_HAND
                    && Math.abs(remote.getX() - setup.attackerPosition().x) <= POSITION_EPSILON
                    && Math.abs(remote.getY() - setup.attackerPosition().y) <= POSITION_EPSILON
                    && Math.abs(remote.getZ() - setup.attackerPosition().z) <= POSITION_EPSILON;
            });

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Capture capture = context.computeOnClient(minecraft -> {
                if (minecraft.level == null) throw new AssertionError("client level unavailable for kinetic capture");
                Entity entity = minecraft.level.getEntity(setup.attacker().entityId());
                if (!(entity instanceof net.minecraft.world.entity.player.Player remote)) {
                    throw new AssertionError("remote kinetic spear attacker missing before production capture");
                }
                KineticWeapon kinetic = remote.getMainHandItem().get(DataComponents.KINETIC_WEAPON);
                if (kinetic == null) {
                    throw new AssertionError("26.1.2 synchronized KINETIC_WEAPON component missing on remote spear");
                }
                KineticWeapon.Condition damage = kinetic.damageConditions()
                    .orElseThrow(() -> new AssertionError("netherite spear lacks kinetic damage condition"));

                var frame = harness.runtime().capture();
                var snapshot = frame.context().world().entities().stream()
                    .filter(candidate -> candidate.id().equals(Integer.toString(setup.attacker().entityId())))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("kinetic spear attacker missing from production frame"));
                return new Capture(
                    kinetic,
                    damage,
                    snapshot.properties(),
                    snapshot.properties().getOrDefault("using_item", "missing"),
                    snapshot.properties().getOrDefault("used_hand", "missing")
                );
            });

            if (!"true".equals(capture.usingItem()) || !"main_hand".equals(capture.usedHand())) {
                throw new AssertionError("production frame lost synchronized kinetic spear use state: " + capture.properties());
            }
            assertProperty(capture.properties(), "spear_kinetic", "true");
            assertProperty(
                capture.properties(),
                "spear_kinetic_contact_cooldown_ticks",
                Integer.toString(capture.kinetic().contactCooldownTicks())
            );
            assertProperty(
                capture.properties(),
                "spear_kinetic_delay_ticks",
                Integer.toString(capture.kinetic().delayTicks())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_multiplier",
                Float.toString(capture.kinetic().damageMultiplier())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_max_use_ticks",
                Integer.toString(capture.damage().maxDurationTicks())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_min_speed",
                Float.toString(capture.damage().minSpeed())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_min_relative_speed",
                Float.toString(capture.damage().minRelativeSpeed())
            );
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
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
                BurstSequenceValidationSupport.removeMockAttacker(server, setup.attacker());
            });
            context.waitTick();
        }
    }

    private static void assertProperty(Map<String, String> properties, String key, String expected) {
        String actual = properties.get(key);
        if (!expected.equals(actual)) {
            throw new AssertionError(
                "production frame omitted/mismatched synchronized kinetic spear property "
                    + key + "; expected=" + expected + " actual=" + actual + " properties=" + properties
            );
        }
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        Vec3 attackerPosition,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record Capture(
        KineticWeapon kinetic,
        KineticWeapon.Condition damage,
        Map<String, String> properties,
        String usingItem,
        String usedHand
    ) {
    }
}
