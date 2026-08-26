package dev.pixelied.survival.validation;

import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Proves an already-charged explosive anchor pre-arms protection before its one-click detonation. */
final class ChargedAnchorBurstSequenceValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;

    private ChargedAnchorBurstSequenceValidationScenarios() {
    }

    static void validateChargedAnchorImmediateUse(
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
                Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 1)
            );
            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, ItemStack.EMPTY);
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 7.0d);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            return new Setup(victim.getUUID(), original, center, anchor, originals, handle);
        });

        try {
            waitForClientPosition(context, setup.center());
            BurstSequenceValidationSupport.waitForClientAttacker(context, setup.attacker());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(setup.anchor()).is(Blocks.RESPAWN_ANCHOR)
                && minecraft.level.getBlockState(setup.anchor()).getValue(RespawnAnchorBlock.CHARGE) == 1);

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean anchorOpportunity = frame.opportunities().stream()
                    .anyMatch(opportunity -> opportunity.family() == OpportunityFamily.RESPAWN_ANCHOR);
                if (!anchorOpportunity) {
                    throw new AssertionError(
                        "pre-arm frame had no charged-anchor opportunity; opportunities="
                            + frame.opportunities().stream()
                                .map(opportunity -> opportunity.family() + ":" + opportunity.id())
                                .toList()
                    );
                }
                if (!frame.actualTimeline().events().isEmpty()) {
                    throw new AssertionError(
                        "charged-anchor precursor is contaminated by active threats: "
                            + frame.actualTimeline().events().stream()
                                .map(event -> event.kind() + ":" + event.id())
                                .toList()
                    );
                }
                return null;
            });

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "charged_respawn_anchor"
            );

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ServerLevel level = (ServerLevel)victim.level();
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before charged-anchor use");
                }
                BlockState charged = level.getBlockState(setup.anchor());
                if (!charged.is(Blocks.RESPAWN_ANCHOR)
                    || charged.getValue(RespawnAnchorBlock.CHARGE) != 1) {
                    throw new AssertionError("charged anchor changed before final hostile interaction");
                }
                victim.invulnerableTime = 0;
                victim.setHealth(4f);
                BlockHitResult hit = new BlockHitResult(
                    setup.anchor().getCenter(), Direction.UP, setup.anchor(), false
                );

                charged.useWithoutItem(level, attacker, hit);
                return new Outcome(
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    level.getBlockState(setup.anchor()).isAir()
                );
            });

            if (!outcome.protectionConsumed()) {
                throw new AssertionError(
                    "charged-anchor immediate use did not consume protection; health=" + outcome.health()
                );
            }
            SurvivalValidationClientGameTest.assertClose(
                "charged_respawn_anchor_zero_delay_pop", 1f, outcome.health(), EPSILON
            );
            if (!outcome.anchorRemoved()) {
                throw new AssertionError("charged respawn anchor was not removed by explosion");
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

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 8; dz++) {
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

    private record Outcome(float health, boolean protectionConsumed, boolean anchorRemoved) {
    }
}
