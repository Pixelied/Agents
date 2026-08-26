package dev.pixelied.survival.validation;

import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exact-runtime bad-respawn sequences that must arm before the final explosive state exists. */
final class BedAnchorBurstSequenceValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;

    private BedAnchorBurstSequenceValidationScenarios() {
    }

    static void validateUnchargedAnchorChargeThenUseWithoutObservationGap(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)victim.level();
            Vec3 original = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 240d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);
            BlockPos anchor = center.offset(0, 0, 2);
            level.setBlockAndUpdate(
                anchor,
                Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 0)
            );
            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, new ItemStack(Items.GLOWSTONE, 1));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 7.0d);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setYRot(0f);
            attacker.setXRot(0f);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            return new Setup(victim.getUUID(), original, center, anchor, originals, handle);
        });

        try {
            waitForClientPosition(context, setup.center());
            BurstSequenceValidationSupport.waitForClientAttacker(context, setup.attacker());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(setup.anchor()).is(Blocks.RESPAWN_ANCHOR)
                && minecraft.level.getBlockState(setup.anchor()).getValue(RespawnAnchorBlock.CHARGE) == 0
                && minecraft.level.getEntity(setup.attacker().entityId()) instanceof net.minecraft.world.entity.player.Player remote
                && remote.getMainHandItem().is(Items.GLOWSTONE));

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            PreArmDiagnostics preArm = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean anchorOpportunity = frame.opportunities().stream()
                    .anyMatch(opportunity -> opportunity.family() == OpportunityFamily.RESPAWN_ANCHOR);
                String actualThreats = frame.actualTimeline().events().stream()
                    .map(event -> event.kind() + ":" + event.id())
                    .toList()
                    .toString();
                String opportunities = frame.opportunities().stream()
                    .map(opportunity -> opportunity.family() + ":" + opportunity.id())
                    .toList()
                    .toString();
                return new PreArmDiagnostics(anchorOpportunity, actualThreats, opportunities);
            });
            if (!preArm.anchorOpportunity()) {
                throw new AssertionError(
                    "pre-arm frame had no uncharged-anchor opportunity; actual=" + preArm.actualThreats()
                        + " opportunities=" + preArm.opportunities()
                );
            }
            if (!"[]".equals(preArm.actualThreats())) {
                throw new AssertionError(
                    "uncharged-anchor precursor test is contaminated by an already-active threat; actual="
                        + preArm.actualThreats() + " opportunities=" + preArm.opportunities()
                );
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "respawn_anchor_charge_then_use"
            );

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ServerLevel level = (ServerLevel)victim.level();
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before anchor charge");
                }
                BlockState before = level.getBlockState(setup.anchor());
                if (!before.is(Blocks.RESPAWN_ANCHOR) || before.getValue(RespawnAnchorBlock.CHARGE) != 0) {
                    throw new AssertionError("anchor was no longer uncharged immediately before hostile sequence");
                }
                victim.invulnerableTime = 0;
                victim.setHealth(4f);

                BlockHitResult hit = new BlockHitResult(
                    setup.anchor().getCenter(), Direction.UP, setup.anchor(), false
                );
                before.useItemOn(
                    attacker.getMainHandItem(),
                    level,
                    attacker,
                    InteractionHand.MAIN_HAND,
                    hit
                );
                BlockState charged = level.getBlockState(setup.anchor());
                boolean chargedSynchronously = charged.is(Blocks.RESPAWN_ANCHOR)
                    && charged.getValue(RespawnAnchorBlock.CHARGE) == 1;
                if (!chargedSynchronously) {
                    throw new AssertionError("real RespawnAnchorBlock charge did not synchronously reach charge 1");
                }

                float seenPercent = ServerExplosion.getSeenPercent(setup.anchor().getCenter(), victim);
                double normalizedDistance = Math.sqrt(victim.distanceToSqr(setup.anchor().getCenter())) / 10.0d;
                double power = (1.0d - normalizedDistance) * seenPercent;
                float rawDamage = (float)(((power * power + power) / 2.0d) * 7.0d * 10.0d + 1.0d);

                // No tick or client observation is allowed between the legal charge and use actions.
                charged.useWithoutItem(level, attacker, hit);
                return new Outcome(
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    level.getBlockState(setup.anchor()).isAir(),
                    seenPercent,
                    rawDamage
                );
            });

            if (!outcome.protectionConsumed()) {
                throw new AssertionError(
                    "zero-delay charge->use anchor sequence did not consume protection; health=" + outcome.health()
                        + " seen=" + outcome.seenPercent() + " raw=" + outcome.rawDamage()
                );
            }
            SurvivalValidationClientGameTest.assertClose(
                "respawn_anchor_zero_delay_pop", 1f, outcome.health(), EPSILON
            );
            if (!outcome.anchorRemoved()) {
                throw new AssertionError("exploding respawn anchor was not removed before entity damage");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel)victim.level();
                    restore(level, setup.originals());
                    SurvivalValidationClientGameTest.reset(victim, 20f);
                    victim.setNoGravity(false);
                    victim.teleportTo(
                        setup.originalPosition().x,
                        setup.originalPosition().y,
                        setup.originalPosition().z
                    );
                }
                BurstSequenceValidationSupport.removeMockAttacker(server, setup.attacker());
            });
            context.waitTick();
        }
    }

    static void validateExplosiveBedPlaceThenUseWithoutObservationGap(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        BedSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 original = victim.position();
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether == null) throw new AssertionError("integrated server has no Nether level for explosive-bed validation");
            BlockPos center = BlockPos.containing(victim.getX(), 200d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(nether, center);
            BlockPos support = center.offset(0, -1, 2);
            nether.setBlockAndUpdate(support, Blocks.OBSIDIAN.defaultBlockState());

            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            boolean teleported = victim.teleportTo(
                nether,
                center.getX() + 0.5d,
                center.getY(),
                center.getZ() + 0.5d,
                Set.of(),
                0f,
                0f,
                true
            );
            if (!teleported) throw new AssertionError("could not move validation victim into Nether");

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, new ItemStack(Items.RED_BED, 1));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 7.0d);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setYRot(0f);
            attacker.setXRot(0f);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            return new BedSetup(victim.getUUID(), original, center, support, originals, handle);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.dimension() == Level.NETHER
                && Math.abs(minecraft.player.getX() - (setup.center().getX() + 0.5d)) <= POSITION_EPSILON
                && Math.abs(minecraft.player.getY() - setup.center().getY()) <= POSITION_EPSILON
                && Math.abs(minecraft.player.getZ() - (setup.center().getZ() + 0.5d)) <= POSITION_EPSILON);
            BurstSequenceValidationSupport.waitForClientAttacker(context, setup.attacker());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(setup.support()).is(Blocks.OBSIDIAN)
                && minecraft.level.getBlockState(setup.support().above()).isAir()
                && minecraft.level.getEntity(setup.attacker().entityId()) instanceof net.minecraft.world.entity.player.Player remote
                && remote.getMainHandItem().is(Items.RED_BED));

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            BedPreArmDiagnostics preArm = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean bedOpportunity = frame.opportunities().stream()
                    .anyMatch(opportunity -> opportunity.family() == OpportunityFamily.BED);
                String actualThreats = frame.actualTimeline().events().stream()
                    .map(event -> event.kind() + ":" + event.id())
                    .toList()
                    .toString();
                String opportunities = frame.opportunities().stream()
                    .map(opportunity -> opportunity.family() + ":" + opportunity.id())
                    .toList()
                    .toString();
                return new BedPreArmDiagnostics(bedOpportunity, actualThreats, opportunities);
            });
            if (!preArm.bedOpportunity()) {
                throw new AssertionError(
                    "pre-arm frame had no explosive-bed opportunity; actual=" + preArm.actualThreats()
                        + " opportunities=" + preArm.opportunities()
                );
            }
            if (!"[]".equals(preArm.actualThreats())) {
                throw new AssertionError(
                    "bed precursor test is contaminated by an already-active threat; actual="
                        + preArm.actualThreats() + " opportunities=" + preArm.opportunities()
                );
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "bed_place_then_use"
            );

            BedOutcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ServerLevel level = (ServerLevel)victim.level();
                if (level.dimension() != Level.NETHER) {
                    throw new AssertionError("bed hostile sequence left the explosive Nether dimension");
                }
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before bed placement");
                }
                victim.invulnerableTime = 0;
                victim.setHealth(4f);

                BlockHitResult supportHit = new BlockHitResult(
                    setup.support().getCenter(), Direction.UP, setup.support(), false
                );
                Items.RED_BED.useOn(new UseOnContext(attacker, InteractionHand.MAIN_HAND, supportHit));
                BlockPos foot = setup.support().above();
                BlockState footState = level.getBlockState(foot);
                if (!footState.is(Blocks.RED_BED)) {
                    throw new AssertionError("real Red Bed BlockItem placement created no foot block");
                }
                Direction facing = footState.getValue(HorizontalDirectionalBlock.FACING);
                BlockPos head = foot.relative(facing);
                if (!level.getBlockState(head).is(Blocks.RED_BED)) {
                    throw new AssertionError("real bed placement created no head block");
                }

                // No tick or client observation is allowed between placement and explosive use.
                footState.useWithoutItem(
                    level,
                    attacker,
                    new BlockHitResult(foot.getCenter(), Direction.UP, foot, false)
                );
                return new BedOutcome(
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    !level.getBlockState(foot).is(Blocks.RED_BED),
                    !level.getBlockState(head).is(Blocks.RED_BED)
                );
            });

            if (!outcome.protectionConsumed()) {
                throw new AssertionError("zero-delay bed place->use sequence did not consume protection; health=" + outcome.health());
            }
            SurvivalValidationClientGameTest.assertClose("bed_zero_delay_pop", 1f, outcome.health(), EPSILON);
            if (!outcome.footRemoved() || !outcome.headRemoved()) {
                throw new AssertionError("exploding bed left a bed half present after the bad-respawn explosion");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                BurstSequenceValidationSupport.removeMockAttacker(server, setup.attacker());
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                ServerLevel nether = server.getLevel(Level.NETHER);
                if (nether != null) restore(nether, setup.originals());
                if (victim != null) {
                    SurvivalValidationClientGameTest.reset(victim, 20f);
                    victim.setNoGravity(false);
                    victim.teleportTo(
                        server.overworld(),
                        setup.originalPosition().x,
                        setup.originalPosition().y,
                        setup.originalPosition().z,
                        Set.<Relative>of(),
                        0f,
                        0f,
                        true
                    );
                }
            });
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD);
            context.waitTick();
        }
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
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

    private static void waitForClientPosition(ClientGameTestContext context, BlockPos center) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - (center.getX() + 0.5d)) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - center.getY()) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - (center.getZ() + 0.5d)) <= POSITION_EPSILON);
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        BlockPos anchor,
        Map<BlockPos, BlockState> originals,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record BedSetup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        BlockPos support,
        Map<BlockPos, BlockState> originals,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record PreArmDiagnostics(
        boolean anchorOpportunity,
        String actualThreats,
        String opportunities
    ) {
    }

    private record BedPreArmDiagnostics(
        boolean bedOpportunity,
        String actualThreats,
        String opportunities
    ) {
    }

    private record Outcome(
        float health,
        boolean protectionConsumed,
        boolean anchorRemoved,
        float seenPercent,
        float rawDamage
    ) {
    }

    private record BedOutcome(
        float health,
        boolean protectionConsumed,
        boolean footRemoved,
        boolean headRemoved
    ) {
    }
}
