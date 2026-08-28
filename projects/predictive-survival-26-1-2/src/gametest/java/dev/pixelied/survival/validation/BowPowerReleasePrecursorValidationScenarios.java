package dev.pixelied.survival.validation;

import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proof that a real synchronized Power V Bow widens the pre-release damage bound. */
final class BowPowerReleasePrecursorValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final int MIN_LEGAL_BOW_USE_TICKS = 3;
    private static final int POWER_LEVEL = 5;
    private static final float VICTIM_HEALTH = 2f;

    private BowPowerReleasePrecursorValidationScenarios() {
    }

    static void validatePowerEnchantmentWidensRealReleaseBound(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 340d, victim.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, VICTIM_HEALTH);
            Vec3 victimPosition = new Vec3(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
            victim.teleportTo(victimPosition.x, victimPosition.y, victimPosition.z);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);

            ItemStack bow = new ItemStack(Items.BOW);
            bow.set(DataComponents.ATTACK_RANGE, new AttackRange(4f, 4f, 4f, 4f, 0f, 1f));
            var power = server.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.POWER);
            bow.enchant(power, POWER_LEVEL);
            int serverPowerLevel = EnchantmentHelper.getItemEnchantmentLevel(power, bow);
            if (serverPowerLevel != POWER_LEVEL) {
                throw new AssertionError(
                    "Power fixture did not create a real level-" + POWER_LEVEL + " enchanted Bow; level=" + serverPowerLevel
                );
            }

            attacker.getInventory().setItem(0, bow);
            attacker.getInventory().setItem(1, new ItemStack(Items.ARROW, 16));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setXRot(0f);
            attacker.setYRot(0f);
            attacker.setYHeadRot(0f);

            // Three draw ticks produce ~0.3225 blocks/tick. Keep the victim's near AABB face
            // 0.32 blocks from the attacker's eye so the first legal arrow can hit on its first tick.
            Vec3 attackerPosition = new Vec3(victimPosition.x, victimPosition.y, victimPosition.z - 0.62d);
            attacker.teleportTo(attackerPosition.x, attackerPosition.y, attackerPosition.z);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
            victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));

            return new Setup(victim.getUUID(), originalPosition, attackerPosition, originals, handle);
        });

        try {
            // ClientboundSetEquipmentPacket is ignored if its target entity has not been added to
            // ClientLevel yet. This is the second mock attacker in the same exact-runtime session,
            // so wait for that entity first and then publish the authoritative enchanted stack.
            BurstSequenceValidationSupport.waitForClientAttacker(context, setup.attacker());
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                BurstSequenceValidationSupport.syncEquipment(victim, attacker);
                victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
                victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));
            });
            waitForClientBaseline(context, setup);
            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                attacker.startUsingItem(InteractionHand.MAIN_HAND);
                attacker.doTick();
                if (attacker.getTicksUsingItem() != 1) {
                    throw new AssertionError(
                        "Power Bow fixture did not reach exactly one authoritative use tick; ticks="
                            + attacker.getTicksUsingItem()
                    );
                }
                var values = attacker.getEntityData().getNonDefaultValues();
                if (values == null) {
                    throw new AssertionError("Power Bow use did not dirty synchronized living-entity state");
                }
                victim.connection.send(new ClientboundSetEntityDataPacket(attacker.getId(), values));
            });

            waitForClientBowUse(context, setup.attacker());

            Precursor precursor = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                String attackerId = Integer.toString(setup.attacker().entityId());
                var attackerSnapshot = frame.context().world().entities().stream()
                    .filter(candidate -> candidate.id().equals(attackerId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Power Bow attacker missing from production world snapshot"));
                LethalOpportunity opportunity = frame.opportunities().stream()
                    .filter(candidate -> candidate.family() == OpportunityFamily.PROJECTILE)
                    .filter(candidate -> "bow_arrow".equals(candidate.evidence().get("release_family")))
                    .filter(candidate -> attackerId.equals(candidate.evidence().get("attacker_id")))
                    .findFirst()
                    .orElse(null);
                boolean planningContainsBow = opportunity != null && frame.planningTimeline().events().stream()
                    .anyMatch(event -> event.id().equals(opportunity.projectedThreat().id()));
                boolean equipCandidate = frame.candidates().stream()
                    .anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance);
                boolean liveArrowThreat = frame.actualTimeline().events().stream()
                    .anyMatch(event -> event.id().startsWith("projectile:") && event.id().contains(attackerId));
                boolean meleeContamination = frame.actualTimeline().events().stream()
                    .anyMatch(event -> event.id().equals("melee:" + attackerId));
                boolean crammingContamination = frame.actualTimeline().events().stream()
                    .anyMatch(event -> event.id().startsWith("env:cramming:"));
                return new Precursor(
                    opportunity,
                    planningContainsBow,
                    equipCandidate,
                    liveArrowThreat,
                    meleeContamination,
                    crammingContamination,
                    attackerSnapshot.properties().getOrDefault("main_hand_bow_power_enchantment_level", "missing"),
                    frame.opportunities().toString(),
                    frame.candidates().toString()
                );
            });

            if (!Integer.toString(POWER_LEVEL).equals(precursor.snapshotPowerLevel())) {
                throw new AssertionError("production snapshot lost the real Power V Bow component: " + precursor);
            }
            if (precursor.liveArrowThreat()) {
                throw new AssertionError("Power Bow precursor was observed only after a projectile already existed");
            }
            if (precursor.meleeContamination()) {
                throw new AssertionError("Power Bow fixture leaked an unrelated generic-melee threat: " + precursor);
            }
            if (precursor.crammingContamination()) {
                throw new AssertionError("Power Bow fixture leaked an unrelated entity-cramming threat: " + precursor);
            }
            if (precursor.opportunity() == null) {
                throw new AssertionError(
                    "production runtime did not create a Power Bow release precursor; opportunities=" + precursor.opportunities()
                );
            }
            if (!Integer.toString(POWER_LEVEL).equals(
                precursor.opportunity().evidence().get("bow_power_enchantment_level")
            )) {
                throw new AssertionError("Power V level was not carried into the release opportunity: " + precursor.opportunity());
            }
            if (precursor.opportunity().projectedThreat().damage().rawDamage().max() < VICTIM_HEALTH) {
                throw new AssertionError(
                    "Power V release bound did not cover a lethal hit on the 2-HP fixture: " + precursor.opportunity()
                );
            }
            if (!precursor.planningContainsBow()) {
                throw new AssertionError("Power Bow precursor was not carried into the production planning timeline");
            }
            if (!precursor.equipCandidate()) {
                throw new AssertionError(
                    "Power Bow precursor did not produce a death-protection candidate; candidates=" + precursor.candidates()
                );
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "bow_power_v_release"
            );

            int serverUseTicks = singleplayer.getServer().computeOnServer(server ->
                BurstSequenceValidationSupport.requireAttacker(server, setup.attacker()).getTicksUsingItem()
            );
            if (serverUseTicks != 1) {
                throw new AssertionError(
                    "mock server Bow use advanced while the production engine armed protection; ticks=" + serverUseTicks
                );
            }

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                attacker.doTick();
                if (attacker.getTicksUsingItem() != 2) {
                    throw new AssertionError(
                        "Power Bow fixture did not advance to authoritative use tick 2; ticks=" + attacker.getTicksUsingItem()
                    );
                }
            });
            context.waitTick();

            Release release = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ServerLevel level = (ServerLevel)victim.level();

                attacker.doTick();
                int useTicks = attacker.getTicksUsingItem();
                if (useTicks != MIN_LEGAL_BOW_USE_TICKS) {
                    throw new AssertionError("Power Bow did not reach the first legal use tick; ticks=" + useTicks);
                }
                float drawPower = BowItem.getPowerForTime(useTicks);
                if (drawPower < 0.1f) {
                    throw new AssertionError("26.1.2 Bow draw was still illegal at use tick " + useTicks + ": " + drawPower);
                }
                var power = server.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.POWER);
                int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(power, attacker.getMainHandItem());
                if (powerLevel != POWER_LEVEL) {
                    throw new AssertionError("server lost Power V immediately before release; level=" + powerLevel);
                }
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before Power Bow release");
                }

                victim.invulnerableTime = 0;
                victim.setHealth(VICTIM_HEALTH);
                int arrowsBefore = nearbyArrows(level, victim);
                attacker.releaseUsingItem();
                int arrowsAfter = nearbyArrows(level, victim);
                return new Release(useTicks, drawPower, powerLevel, arrowsBefore, arrowsAfter);
            });
            if (release.arrowsAfter() <= release.arrowsBefore()) {
                throw new AssertionError("real Power Bow release did not spawn an arrow: " + release);
            }

            Outcome outcome = null;
            for (int tick = 0; tick < 12; tick++) {
                context.waitTick();
                outcome = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                    return new Outcome(
                        victim.getHealth(),
                        BurstSequenceValidationSupport.protectedInHand(victim),
                        victim.isAlive()
                    );
                });
                if (!outcome.protectedOnServer() || !outcome.alive()) break;
            }
            if (outcome == null) throw new AssertionError("Power Bow release produced no observable server outcome");
            if (!outcome.alive()) {
                throw new AssertionError("victim died despite precursor-established protection from Power V Bow: " + outcome);
            }
            if (outcome.protectedOnServer()) {
                throw new AssertionError(
                    "first-legal Power V arrow did not consume protection on a 2-HP victim; "
                        + "an unenchanted first-legal arrow would deal only one raw damage: " + outcome
                );
            }
            SurvivalValidationClientGameTest.assertClose("bow_power_v_first_legal_release_pop", 2f, outcome.health(), EPSILON);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel)victim.level();
                    for (AbstractArrow arrow : level.getEntitiesOfClass(
                        AbstractArrow.class,
                        victim.getBoundingBox().inflate(8d)
                    )) {
                        arrow.discard();
                    }
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
                BurstSequenceValidationSupport.removeMockAttacker(server, setup.attacker());
            });
            context.waitTick();
        }
    }

    private static void waitForClientBaseline(ClientGameTestContext context, Setup setup) {
        ClientBaseline last = null;
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            last = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    return new ClientBaseline(
                        false, false, false, false, false,
                        "unavailable", "unavailable", "unavailable", -1,
                        "unavailable", "unavailable", "unavailable"
                    );
                }
                Entity attacker = minecraft.level.getEntity(setup.attacker().entityId());
                if (!(attacker instanceof net.minecraft.world.entity.player.Player remote)) {
                    return new ClientBaseline(
                        false, false, false, false, false,
                        "entity=" + attacker, "unavailable", "unavailable",
                        minecraft.player.getInventory().getSelectedSlot(),
                        minecraft.player.getInventory().getItem(0).toString(),
                        minecraft.player.getInventory().getItem(1).toString(),
                        minecraft.player.getOffhandItem().toString()
                    );
                }
                ItemStack remoteMain = remote.getMainHandItem();
                boolean bowVisible = remoteMain.is(Items.BOW);
                boolean powerVisible = remoteMain.getEnchantments().entrySet().stream()
                    .anyMatch(entry -> entry.getKey().is(Enchantments.POWER) && entry.getIntValue() == POWER_LEVEL);
                boolean positionReady = Math.abs(remote.getX() - setup.attackerPosition().x) <= POSITION_EPSILON
                    && Math.abs(remote.getY() - setup.attackerPosition().y) <= POSITION_EPSILON
                    && Math.abs(remote.getZ() - setup.attackerPosition().z) <= POSITION_EPSILON;
                boolean inventoryReady = minecraft.player.getInventory().getSelectedSlot() == 0
                    && minecraft.player.getInventory().getItem(0).is(Items.STICK)
                    && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                    && minecraft.player.getOffhandItem().isEmpty();
                return new ClientBaseline(
                    bowVisible && powerVisible && positionReady && inventoryReady,
                    bowVisible,
                    powerVisible,
                    positionReady,
                    inventoryReady,
                    remoteMain.toString(),
                    remoteMain.getEnchantments().toString(),
                    remote.position().toString(),
                    minecraft.player.getInventory().getSelectedSlot(),
                    minecraft.player.getInventory().getItem(0).toString(),
                    minecraft.player.getInventory().getItem(1).toString(),
                    minecraft.player.getOffhandItem().toString()
                );
            });
            if (last.ready()) return;
            context.waitTick();
        }
        throw new AssertionError("Power Bow client baseline never converged: " + last);
    }

    private static void waitForClientBowUse(
        ClientGameTestContext context,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
        context.waitFor(minecraft -> minecraft.level != null
            && minecraft.level.getEntity(attacker.entityId()) instanceof net.minecraft.world.entity.player.Player remote
            && remote.getMainHandItem().is(Items.BOW)
            && remote.isUsingItem()
            && remote.getUsedItemHand() == InteractionHand.MAIN_HAND);
    }

    private static int nearbyArrows(ServerLevel level, ServerPlayer victim) {
        return level.getEntitiesOfClass(AbstractArrow.class, victim.getBoundingBox().inflate(8d)).size();
    }

    private static Map<BlockPos, BlockState> prepareArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = center.getY() - 1; y <= center.getY() + 3; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(
                        pos,
                        y == center.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                        2
                    );
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
        Vec3 attackerPosition,
        Map<BlockPos, BlockState> originals,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record Precursor(
        LethalOpportunity opportunity,
        boolean planningContainsBow,
        boolean equipCandidate,
        boolean liveArrowThreat,
        boolean meleeContamination,
        boolean crammingContamination,
        String snapshotPowerLevel,
        String opportunities,
        String candidates
    ) {
    }

    private record Release(
        int serverUseTicks,
        float drawPower,
        int powerLevel,
        int arrowsBefore,
        int arrowsAfter
    ) {
    }

    private record Outcome(float health, boolean protectedOnServer, boolean alive) {
    }

    private record ClientBaseline(
        boolean ready,
        boolean bowVisible,
        boolean powerVisible,
        boolean positionReady,
        boolean inventoryReady,
        String remoteMainHand,
        String remoteEnchantments,
        String remotePosition,
        int victimSelectedSlot,
        String victimSlot0,
        String victimSlot1,
        String victimOffhand
    ) {
    }
}
