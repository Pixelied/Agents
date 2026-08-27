package dev.pixelied.survival.validation;

import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proof that synchronized Bow use pre-arms before the first legal server release. */
final class BowReleasePrecursorValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final int MIN_LEGAL_BOW_USE_TICKS = 3;

    private BowReleasePrecursorValidationScenarios() {
    }

    static void validatePrearmsBeforeFirstLegalRelease(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 324d, victim.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 1f);
            Vec3 victimPosition = new Vec3(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
            victim.teleportTo(victimPosition.x, victimPosition.y, victimPosition.z);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            ItemStack bow = new ItemStack(Items.BOW);
            // This scenario proves the Bow release precursor, not generic melee prediction. Normal
            // ServerboundAttackPacket validation adds a 3-block buffer to the held AttackRange, so
            // zero reach would still admit this close hit. A 4-block minimum makes the 0.32-block
            // eye-to-AABB distance genuinely illegal (minimum after buffer = 1 block) while Bow
            // projectile release remains unchanged.
            bow.set(DataComponents.ATTACK_RANGE, new AttackRange(4f, 4f, 4f, 4f, 0f, 1f));
            attacker.getInventory().setItem(0, bow);
            attacker.getInventory().setItem(1, new ItemStack(Items.ARROW, 16));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setXRot(0f);
            attacker.setYRot(0f);
            attacker.setYHeadRot(0f);

            // At three draw ticks the 26.1.2 Bow speed is only ~0.3225 blocks/tick. Keep the
            // victim's near AABB face 0.32 blocks from the attacker's eye: just inside first-tick
            // projectile reach while keeping the two 0.6-block-wide player AABBs non-overlapping.
            Vec3 attackerPosition = new Vec3(victimPosition.x, victimPosition.y, victimPosition.z - 0.62d);
            attacker.teleportTo(attackerPosition.x, attackerPosition.y, attackerPosition.z);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
            victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));

            return new Setup(
                victim.getUUID(),
                originalPosition,
                center,
                attackerPosition,
                originals,
                handle
            );
        });

        try {
            waitForClientBaseline(context, setup);
            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);

            // Begin a real server-side Bow use and advance exactly one LivingEntity use tick before
            // publishing the synchronized use flag. This gives the runtime a genuine pre-release
            // state where the server is already ahead of the freshly observed client use counter.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                attacker.startUsingItem(InteractionHand.MAIN_HAND);
                attacker.doTick();
                if (attacker.getTicksUsingItem() != 1) {
                    throw new AssertionError(
                        "Bow fixture did not reach exactly one authoritative use tick; ticks="
                            + attacker.getTicksUsingItem()
                    );
                }
                var values = attacker.getEntityData().getNonDefaultValues();
                if (values == null) {
                    throw new AssertionError("Bow use did not dirty synchronized living-entity state");
                }
                victim.connection.send(new ClientboundSetEntityDataPacket(attacker.getId(), values));
            });

            waitForClientBowUse(context, setup.attacker());
            int serverUseTicksAtObservation = serverUseTicks(singleplayer, setup.attacker());
            if (serverUseTicksAtObservation != 1) {
                throw new AssertionError(
                    "server Bow use advanced while waiting for the synchronized precursor; ticks="
                        + serverUseTicksAtObservation
                );
            }

            Precursor precursor = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                String attackerId = Integer.toString(setup.attacker().entityId());
                var attackerSnapshot = frame.context().world().entities().stream()
                    .filter(candidate -> candidate.id().equals(attackerId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Bow attacker missing from production world snapshot"));
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
                    attackerSnapshot.properties().getOrDefault("using_item", "missing"),
                    attackerSnapshot.properties().getOrDefault("used_hand", "missing"),
                    attackerSnapshot.properties().getOrDefault("client_observed_use_ticks", "missing"),
                    frame.opportunities().toString(),
                    frame.candidates().toString()
                );
            });

            if (!"true".equals(precursor.usingItem()) || !"main_hand".equals(precursor.usedHand())) {
                throw new AssertionError("production snapshot lost synchronized Bow-use state: " + precursor);
            }
            if (precursor.liveArrowThreat()) {
                throw new AssertionError("Bow precursor was observed only after a projectile already existed");
            }
            if (precursor.meleeContamination()) {
                throw new AssertionError("Bow precursor fixture leaked an unrelated generic-melee threat: " + precursor);
            }
            if (precursor.crammingContamination()) {
                throw new AssertionError("Bow precursor fixture leaked an unrelated entity-cramming threat: " + precursor);
            }
            if (precursor.opportunity() == null) {
                throw new AssertionError(
                    "production runtime did not create a Bow release precursor before legal release; opportunities="
                        + precursor.opportunities()
                );
            }
            if (!precursor.planningContainsBow()) {
                throw new AssertionError("Bow precursor was not carried into the production planning timeline");
            }
            if (!precursor.equipCandidate()) {
                throw new AssertionError(
                    "Bow precursor did not produce a death-protection candidate; candidates=" + precursor.candidates()
                );
            }
            if (precursor.opportunity().projectedThreat().impact().earliest() > 2L) {
                throw new AssertionError(
                    "Bow precursor did not cover the first legal server release from use tick 1; impact="
                        + precursor.opportunity().projectedThreat().impact()
                        + " observedUseTicks=" + precursor.clientObservedUseTicks()
                );
            }

            // Let the real production engine react for one concurrent tick while the hostile player
            // advances from authoritative use tick 1 -> 2. Protection must already be recognized on
            // the server here, one tick before BowItem's first legal release at tick 3.
            context.runOnClient(minecraft -> harness.engine().tick());
            EngineDiagnostics diagnostics = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().lastFrame()
                    .orElseThrow(() -> new AssertionError("engine tick did not retain its Bow decision frame"));
                int selected = minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot();
                String inventory = minecraft.player == null
                    ? "player=null"
                    : "selected=" + selected
                        + ",slot0=" + minecraft.player.getInventory().getItem(0)
                        + ",slot1=" + minecraft.player.getInventory().getItem(1)
                        + ",main=" + minecraft.player.getMainHandItem()
                        + ",off=" + minecraft.player.getOffhandItem();
                return new EngineDiagnostics(
                    selected,
                    harness.engine().currentPlan().toString(),
                    harness.engine().executionStatus().toString(),
                    harness.engine().history().snapshot().toString(),
                    frame.context().timing().toString(),
                    frame.actualTimeline().events().toString(),
                    frame.opportunities().toString(),
                    frame.planningTimeline().events().toString(),
                    frame.candidates().toString(),
                    inventory
                );
            });
            singleplayer.getServer().runOnServer(server ->
                BurstSequenceValidationSupport.requireAttacker(server, setup.attacker()).doTick()
            );
            context.waitTick();

            PreRelease preRelease = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                return new PreRelease(
                    attacker.getTicksUsingItem(),
                    BurstSequenceValidationSupport.protectedInHand(victim),
                    victim.getInventory().getSelectedSlot()
                );
            });
            if (preRelease.serverUseTicks() != 2) {
                throw new AssertionError("Bow fixture did not advance to authoritative use tick 2: " + preRelease);
            }
            if (!preRelease.protectedOnServer()) {
                throw new AssertionError(
                    "production engine failed to establish server-authoritative protection before first legal Bow release; "
                        + preRelease + "; engine=" + diagnostics
                );
            }

            Release release = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ServerLevel level = (ServerLevel) victim.level();

                attacker.doTick();
                int useTicks = attacker.getTicksUsingItem();
                if (useTicks != MIN_LEGAL_BOW_USE_TICKS) {
                    throw new AssertionError("Bow release did not occur at the first legal use tick; ticks=" + useTicks);
                }
                float power = BowItem.getPowerForTime(useTicks);
                if (power < 0.1f) {
                    throw new AssertionError("26.1.2 Bow power was still illegal at use tick " + useTicks + ": " + power);
                }
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection immediately before Bow release");
                }

                victim.invulnerableTime = 0;
                victim.setHealth(1f);
                int arrowsBefore = nearbyArrows(level, victim);
                attacker.releaseUsingItem();
                int arrowsAfter = nearbyArrows(level, victim);
                return new Release(useTicks, power, arrowsBefore, arrowsAfter);
            });
            if (release.arrowsAfter() <= release.arrowsBefore()) {
                throw new AssertionError("real BowItem.releaseUsing did not spawn an arrow at the first legal tick: " + release);
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
            if (outcome == null) throw new AssertionError("Bow release produced no observable server outcome");
            if (!outcome.alive()) {
                throw new AssertionError("victim died despite precursor-established death protection: " + outcome);
            }
            if (outcome.protectedOnServer()) {
                throw new AssertionError("first-legal-tick Bow arrow never consumed server-authoritative protection: " + outcome);
            }
            SurvivalValidationClientGameTest.assertClose("bow_first_legal_release_pop", 1f, outcome.health(), EPSILON);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel) victim.level();
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
        context.waitFor(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) return false;
            Entity attacker = minecraft.level.getEntity(setup.attacker().entityId());
            return attacker instanceof net.minecraft.world.entity.player.Player remote
                && remote.getMainHandItem().is(Items.BOW)
                && Math.abs(remote.getX() - setup.attackerPosition().x) <= POSITION_EPSILON
                && Math.abs(remote.getY() - setup.attackerPosition().y) <= POSITION_EPSILON
                && Math.abs(remote.getZ() - setup.attackerPosition().z) <= POSITION_EPSILON
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(0).is(Items.STICK)
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.player.getOffhandItem().isEmpty();
        });
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

    private static int serverUseTicks(
        TestSingleplayerContext singleplayer,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
        return singleplayer.getServer().computeOnServer(server ->
            BurstSequenceValidationSupport.requireAttacker(server, attacker).getTicksUsingItem()
        );
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
        BlockPos center,
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
        String usingItem,
        String usedHand,
        String clientObservedUseTicks,
        String opportunities,
        String candidates
    ) {
    }

    private record EngineDiagnostics(
        int clientSelectedSlot,
        String currentPlan,
        String executionStatus,
        String history,
        String timing,
        String actualThreats,
        String opportunities,
        String planningThreats,
        String candidates,
        String inventory
    ) {
    }

    private record PreRelease(int serverUseTicks, boolean protectedOnServer, int selectedSlot) {
    }

    private record Release(int serverUseTicks, float power, int arrowsBefore, int arrowsAfter) {
    }

    private record Outcome(float health, boolean protectedOnServer, boolean alive) {
    }
}
