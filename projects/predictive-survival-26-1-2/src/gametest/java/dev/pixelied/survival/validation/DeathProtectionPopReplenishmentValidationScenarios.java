package dev.pixelied.survival.validation;

import dev.pixelied.survival.execution.DeathProtectionPopTracker;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Exact-runtime proof that one observed Totem pop cannot suppress replenishment before a second lethal hit. */
final class DeathProtectionPopReplenishmentValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;

    private DeathProtectionPopReplenishmentValidationScenarios() {
    }

    static void validateFirstPopRearmsBeforeSecondLethalHit(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 original = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 236d, victim.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center);

            SurvivalValidationClientGameTest.reset(victim, 4f);
            victim.setNoGravity(true);
            victim.setDeltaMovement(Vec3.ZERO);
            victim.fallDistance = 0d;
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
            victim.getInventory().clearContent();
            victim.getInventory().setSelectedSlot(0);
            victim.getInventory().setItem(0, new ItemStack(Items.STICK));
            victim.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            victim.getInventory().setItem(2, new ItemStack(Items.TOTEM_OF_UNDYING));
            victim.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            victim.containerMenu.broadcastChanges();
            return new Setup(victim.getUUID(), original, center, originals);
        });

        AtomicInteger crystalId = new AtomicInteger(-1);
        BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
        try {
            waitForClientBaseline(context, setup.center());
            BurstSequenceValidationSupport.ensureSelectedSlot(
                context,
                singleplayer,
                setup.victimId(),
                1,
                "death_protection_pop_first_arm"
            );

            // Bind the runtime to the current LocalPlayer and capture the authoritative pre-pop hand
            // before event 35 arrives. Production normally has already done this on prior client ticks.
            context.runOnClient(minecraft -> {
                DeathProtectionPopTracker.global().reset();
                harness.runtime().capture();
            });

            FirstPop first = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                victim.invulnerableTime = 0;
                victim.setHealth(4f);
                boolean accepted = victim.hurtServer(level, victim.damageSources().generic(), 100f);
                return new FirstPop(
                    accepted,
                    victim.getHealth(),
                    victim.getInventory().getSelectedSlot(),
                    victim.getInventory().getItem(1).isEmpty(),
                    victim.getInventory().getItem(2).is(Items.TOTEM_OF_UNDYING),
                    !victim.isDeadOrDying()
                );
            });
            if (!first.accepted()) throw new AssertionError("first lethal damage was rejected");
            if (!first.alive() || Math.abs(1f - first.health()) > EPSILON) {
                throw new AssertionError("first lethal hit did not produce a vanilla Totem rescue: " + first);
            }
            if (first.selectedSlot() != 1 || !first.firstTotemConsumed() || !first.replacementStillPresent()) {
                throw new AssertionError("first pop did not consume only the armed Totem: " + first);
            }

            context.waitFor(minecraft -> DeathProtectionPopTracker.global().generation() == 1L);

            // Make the next crystal unambiguously lethal without relying on the temporary absorption
            // and regeneration granted by the first Totem. Inventory/equipment are intentionally kept.
            crystalId.set(singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                victim.removeAllEffects();
                victim.setAbsorptionAmount(0f);
                victim.setHealth(4f);
                victim.invulnerableTime = 0;
                EndCrystal crystal = new EndCrystal(
                    level,
                    victim.getX(),
                    victim.getY() + 0.9d,
                    victim.getZ() + 2.5d
                );
                level.addFreshEntity(crystal);
                return crystal.getId();
            }));
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(crystalId.get()) instanceof EndCrystal);

            tickUntilReplacementIsServerAuthoritative(context, singleplayer, setup.victimId(), harness);

            long generationBeforeSecondHit = context.computeOnClient(minecraft ->
                DeathProtectionPopTracker.global().generation());
            if (generationBeforeSecondHit != 1L) {
                throw new AssertionError(
                    "unexpected pop generation before second lethal hit: " + generationBeforeSecondHit
                );
            }

            SecondPop second = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                Entity entity = level.getEntity(crystalId.get());
                if (!(entity instanceof EndCrystal crystal)) {
                    throw new AssertionError("second lethal crystal disappeared before detonation");
                }
                if (victim.getInventory().getSelectedSlot() != 2
                    || !victim.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("replacement Totem was not server-authoritative before second hit");
                }

                victim.removeAllEffects();
                victim.setAbsorptionAmount(0f);
                victim.setHealth(4f);
                victim.invulnerableTime = 0;
                boolean accepted = crystal.hurtServer(level, victim.damageSources().generic(), 1f);
                return new SecondPop(
                    accepted,
                    victim.getHealth(),
                    victim.getInventory().getItem(2).isEmpty(),
                    !victim.isDeadOrDying(),
                    crystal.isRemoved()
                );
            });
            if (!second.accepted()) throw new AssertionError("second lethal crystal detonation was rejected");
            if (!second.crystalRemoved()) throw new AssertionError("second lethal crystal did not detonate");
            if (!second.alive() || Math.abs(1f - second.health()) > EPSILON || !second.replacementConsumed()) {
                throw new AssertionError("replacement Totem did not rescue the second lethal hit: " + second);
            }

            context.waitFor(minecraft -> DeathProtectionPopTracker.global().generation() == 2L);
            boolean serverAlive = singleplayer.getServer().computeOnServer(server ->
                !requireVictim(server, setup.victimId()).isDeadOrDying()
            );
            if (!serverAlive) throw new AssertionError("victim died after the second observed Totem pop");
        } finally {
            int cleanupCrystalId = crystalId.get();
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
                DeathProtectionPopTracker.global().reset();
                harness.runtime().reset();
                if (minecraft.player != null) minecraft.player.getInventory().setSelectedSlot(0);
            });
            context.waitTick();
        }
    }

    private static void tickUntilReplacementIsServerAuthoritative(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        BurstSequenceValidationSupport.RuntimeHarness harness
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            context.runOnClient(minecraft -> harness.engine().tick());
            context.waitTick();
            boolean armed = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = requireVictim(server, victimId);
                return victim.getInventory().getSelectedSlot() == 2
                    && victim.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
            });
            if (armed) return;
        }

        String clientDiagnostics = context.computeOnClient(minecraft -> {
            var frame = harness.runtime().capture();
            return "generation=" + DeathProtectionPopTracker.global().generation()
                + ",unresolved=" + DeathProtectionPopTracker.global().consumptionUnresolved()
                + ",selected=" + (minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot())
                + ",actual=" + frame.actualTimeline().events()
                + ",planning=" + frame.planningTimeline().events()
                + ",candidates=" + frame.candidates()
                + ",plan=" + harness.engine().currentPlan()
                + ",status=" + harness.engine().executionStatus();
        });
        String serverDiagnostics = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = requireVictim(server, victimId);
            return "selected=" + victim.getInventory().getSelectedSlot()
                + ",slot1=" + victim.getInventory().getItem(1)
                + ",slot2=" + victim.getInventory().getItem(2)
                + ",main=" + victim.getMainHandItem()
                + ",off=" + victim.getOffhandItem();
        });
        throw new AssertionError(
            "production engine did not replenish protection after first pop; client={"
                + clientDiagnostics + "}; server={" + serverDiagnostics + "}"
        );
    }

    private static void waitForClientBaseline(ClientGameTestContext context, BlockPos center) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - (center.getX() + 0.5d)) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - center.getY()) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - (center.getZ() + 0.5d)) <= POSITION_EPSILON
            && minecraft.player.getInventory().getItem(0).is(Items.STICK)
            && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
            && minecraft.player.getInventory().getItem(2).is(Items.TOTEM_OF_UNDYING)
            && minecraft.player.getOffhandItem().isEmpty());
    }

    private static ServerPlayer requireVictim(net.minecraft.server.MinecraftServer server, UUID victimId) {
        ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
        if (victim == null) throw new AssertionError("victim disappeared during pop replenishment validation");
        return victim;
    }

    private static Map<BlockPos, BlockState> prepareArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = center.getY() - 1; y <= center.getY() + 3; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, y == center.getY() - 1
                        ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 2);
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

    private record FirstPop(
        boolean accepted,
        float health,
        int selectedSlot,
        boolean firstTotemConsumed,
        boolean replacementStillPresent,
        boolean alive
    ) {
    }

    private record SecondPop(
        boolean accepted,
        float health,
        boolean replacementConsumed,
        boolean alive,
        boolean crystalRemoved
    ) {
    }
}
