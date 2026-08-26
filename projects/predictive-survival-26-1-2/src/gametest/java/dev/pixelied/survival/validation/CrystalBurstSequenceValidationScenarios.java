package dev.pixelied.survival.validation;

import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Proves protection is established from a legal crystal precursor before any crystal exists. */
final class CrystalBurstSequenceValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;

    private CrystalBurstSequenceValidationScenarios() {
    }

    static void validatePrecursorThenZeroDelayPlaceBreak(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)victim.level();
            Vec3 original = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 240d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);
            BlockPos support = center.offset(0, -1, 2);
            level.setBlockAndUpdate(support, Blocks.OBSIDIAN.defaultBlockState());
            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, new ItemStack(Items.END_CRYSTAL, 2));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 7.0d);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setYRot(0f);
            attacker.setXRot(0f);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            return new Setup(victim.getUUID(), original, center, support, originals, handle);
        });

        try {
            waitForClientPosition(context, setup.center());
            BurstSequenceValidationSupport.waitForClientAttacker(context, setup.attacker());
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(setup.support()).is(Blocks.OBSIDIAN)
                && minecraft.level.getEntity(setup.attacker().entityId()) instanceof net.minecraft.world.entity.player.Player remote
                && remote.getMainHandItem().is(Items.END_CRYSTAL));

            boolean noCrystalBeforeArm = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                return ((ServerLevel)victim.level()).getEntitiesOfClass(
                    EndCrystal.class,
                    new AABB(
                        setup.support().getX() - 2,
                        setup.support().getY(),
                        setup.support().getZ() - 2,
                        setup.support().getX() + 3,
                        setup.support().getY() + 4,
                        setup.support().getZ() + 3
                    )
                ).isEmpty();
            });
            if (!noCrystalBeforeArm) {
                throw new AssertionError("crystal burst precursor test started with an existing crystal");
            }

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            PreArmDiagnostics preArm = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean crystalOpportunity = frame.opportunities().stream()
                    .anyMatch(opportunity -> opportunity.family() == OpportunityFamily.CRYSTAL);
                String actualThreats = frame.actualTimeline().events().stream()
                    .map(event -> event.kind() + ":" + event.id())
                    .toList()
                    .toString();
                String opportunities = frame.opportunities().stream()
                    .map(opportunity -> opportunity.family() + ":" + opportunity.id())
                    .toList()
                    .toString();
                return new PreArmDiagnostics(crystalOpportunity, actualThreats, opportunities);
            });
            if (!preArm.crystalOpportunity()) {
                throw new AssertionError(
                    "pre-arm frame had no crystal opportunity; actual=" + preArm.actualThreats()
                        + " opportunities=" + preArm.opportunities()
                );
            }
            if (!"[]".equals(preArm.actualThreats())) {
                throw new AssertionError(
                    "crystal precursor test is contaminated by an already-active threat; actual="
                        + preArm.actualThreats() + " opportunities=" + preArm.opportunities()
                );
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "crystal_place_break"
            );

            boolean stillNoCrystal = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                return ((ServerLevel)victim.level()).getEntitiesOfClass(
                    EndCrystal.class,
                    new AABB(
                        setup.support().getX(),
                        setup.support().getY(),
                        setup.support().getZ(),
                        setup.support().getX() + 1,
                        setup.support().getY() + 3,
                        setup.support().getZ() + 1
                    )
                ).isEmpty();
            });
            if (!stillNoCrystal) {
                throw new AssertionError("protection was not established strictly before crystal placement");
            }

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ServerLevel level = (ServerLevel)victim.level();
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before crystal placement");
                }
                victim.invulnerableTime = 0;
                victim.setHealth(4f);

                Items.END_CRYSTAL.useOn(new UseOnContext(
                    attacker,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(setup.support().getCenter(), Direction.UP, setup.support(), false)
                ));
                AABB placementBox = new AABB(
                    setup.support().getX(),
                    setup.support().getY() + 1,
                    setup.support().getZ(),
                    setup.support().getX() + 1,
                    setup.support().getY() + 3,
                    setup.support().getZ() + 1
                );
                EndCrystal crystal = level.getEntitiesOfClass(EndCrystal.class, placementBox).stream()
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("real EndCrystalItem placement created no crystal"));
                float seenPercent = ServerExplosion.getSeenPercent(crystal.position(), victim);
                double normalizedDistance = Math.sqrt(victim.distanceToSqr(crystal.position())) / 12.0d;
                double power = (1.0d - normalizedDistance) * seenPercent;
                float rawDamage = (float)(((power * power + power) / 2.0d) * 7.0d * 12.0d + 1.0d);
                Vec3 victimPosition = victim.position();
                Vec3 crystalPosition = crystal.position();
                boolean accepted = crystal.hurtServer(level, attacker.damageSources().playerAttack(attacker), 1f);
                return new Outcome(
                    accepted,
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    crystal.isRemoved(),
                    seenPercent,
                    rawDamage,
                    victimPosition,
                    crystalPosition
                );
            });

            if (!outcome.attackAccepted()) throw new AssertionError("immediate hostile crystal break was rejected");
            if (!outcome.protectionConsumed()) {
                throw new AssertionError(
                    "crystal zero-delay sequence did not consume protection; health=" + outcome.health()
                        + " seen=" + outcome.seenPercent() + " raw=" + outcome.rawDamage()
                        + " victim=" + outcome.victimPosition() + " crystal=" + outcome.crystalPosition()
                        + " crystalRemoved=" + outcome.crystalRemoved()
                );
            }
            if (Math.abs(1f - outcome.health()) > EPSILON) {
                throw new AssertionError(
                    "crystal_zero_delay_pop expected=1.0 actual=" + outcome.health()
                        + " seen=" + outcome.seenPercent() + " raw=" + outcome.rawDamage()
                        + " victim=" + outcome.victimPosition() + " crystal=" + outcome.crystalPosition()
                );
            }
            if (!outcome.crystalRemoved()) {
                throw new AssertionError("crystal zero-delay sequence did not remove crystal");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel)victim.level();
                    for (Entity entity : level.getEntitiesOfClass(EndCrystal.class, new AABB(
                        setup.center().getX() - 5,
                        setup.center().getY() - 2,
                        setup.center().getZ() - 5,
                        setup.center().getX() + 6,
                        setup.center().getY() + 5,
                        setup.center().getZ() + 6
                    ))) {
                        entity.discard();
                    }
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
        BlockPos support,
        Map<BlockPos, BlockState> originals,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record PreArmDiagnostics(
        boolean crystalOpportunity,
        String actualThreats,
        String opportunities
    ) {
    }

    private record Outcome(
        boolean attackAccepted,
        float health,
        boolean protectionConsumed,
        boolean crystalRemoved,
        float seenPercent,
        float rawDamage,
        Vec3 victimPosition,
        Vec3 crystalPosition
    ) {
    }
}
