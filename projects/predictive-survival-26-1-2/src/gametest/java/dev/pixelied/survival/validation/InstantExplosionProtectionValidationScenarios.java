package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.timeline.ThreatEvent;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.function.Predicate;

final class InstantExplosionProtectionValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private InstantExplosionProtectionValidationScenarios() {
    }

    static void validateVisibleCrystalArmsTotemBeforeDetonation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int crystalId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            prepareArmingFixture(player);
            ServerLevel level = (ServerLevel) player.level();
            EndCrystal crystal = new EndCrystal(level, player.getX() + 2d, player.getY() + 1d, player.getZ());
            level.addFreshEntity(crystal);
            return crystal.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.getEntity(crystalId) instanceof EndCrystal
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            armFromProductionRuntime(
                context,
                event -> event.id().equals("explosion:" + crystalId),
                "End Crystal"
            );
            waitForServerSelectedTotem(context, singleplayer, "End Crystal");

            PopOutcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(crystalId);
                if (!(entity instanceof EndCrystal crystal)) {
                    throw new AssertionError("End Crystal disappeared before controlled detonation");
                }
                requireServerArmed(player, "End Crystal");
                crystal.hurtServer((ServerLevel) player.level(), player.damageSources().playerAttack(player), 1f);
                return popOutcome(player);
            });
            assertTotemPop("visible_crystal_preemptive_totem", outcome);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity crystal = player.level().getEntity(crystalId);
                if (crystal != null) crystal.discard();
                cleanupArmingFixture(player);
            });
            context.waitTick();
        }
    }

    static void validateVisibleAnchorArmsTotemBeforeDetonation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        AnchorSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            prepareArmingFixture(player);
            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = player.blockPosition().offset(2, 0, 0);
            BlockState original = level.getBlockState(pos);
            level.setBlockAndUpdate(
                pos,
                Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 1)
            );
            return new AnchorSetup(pos, original);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.getBlockState(setup.pos()).is(Blocks.RESPAWN_ANCHOR)
                && minecraft.level.getBlockState(setup.pos()).getValue(RespawnAnchorBlock.CHARGE) == 1
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            armFromProductionRuntime(
                context,
                event -> event.id().startsWith("explosion:block:minecraft:respawn_anchor:"),
                "charged Respawn Anchor"
            );
            waitForServerSelectedTotem(context, singleplayer, "charged Respawn Anchor");

            PopOutcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                requireServerArmed(player, "charged Respawn Anchor");
                BlockState state = level.getBlockState(setup.pos());
                if (!state.is(Blocks.RESPAWN_ANCHOR)) {
                    throw new AssertionError("Respawn Anchor disappeared before controlled detonation");
                }
                state.useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(setup.pos().getCenter(), Direction.UP, setup.pos(), false)
                );
                return popOutcome(player);
            });
            assertTotemPop("visible_anchor_preemptive_totem", outcome);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                level.setBlockAndUpdate(setup.pos(), setup.original());
                cleanupArmingFixture(player);
            });
            context.waitTick();
        }
    }

    static void validateVisibleNetherBedArmsTotemBeforeDetonation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        BedSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel overworld = (ServerLevel) player.level();
            ReturnPoint returnPoint = new ReturnPoint(
                overworld.dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
            );
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether == null) throw new AssertionError("integrated server has no Nether level for explosive-bed validation");

            BlockPos playerBase = new BlockPos(0, 82, 0);
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    nether.setBlock(new BlockPos(x, playerBase.getY() - 1, z), Blocks.OBSIDIAN.defaultBlockState(), 2);
                    for (int y = playerBase.getY(); y <= playerBase.getY() + 3; y++) {
                        nether.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            boolean teleported = player.teleportTo(
                nether,
                playerBase.getX() + 0.5d,
                playerBase.getY(),
                playerBase.getZ() + 0.5d,
                Set.of(),
                0f,
                0f,
                true
            );
            if (!teleported) throw new AssertionError("could not move GameTest player to Nether");
            prepareArmingFixture(player);

            Direction facing = Direction.EAST;
            BlockPos head = playerBase.offset(2, 0, 0);
            BlockPos foot = head.relative(facing.getOpposite());
            BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
            BlockState headState = Blocks.RED_BED.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.HEAD);
            nether.setBlock(foot, footState, 2);
            nether.setBlock(head, headState, 2);
            return new BedSetup(head, foot, returnPoint);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.dimension() == Level.NETHER
                && minecraft.level.getBlockState(setup.head()).is(Blocks.RED_BED)
                && minecraft.level.getBlockState(setup.head()).getValue(BedBlock.PART) == BedPart.HEAD
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            armFromProductionRuntime(
                context,
                event -> event.id().startsWith("explosion:block:minecraft:red_bed:"),
                "explosive Nether bed"
            );
            waitForServerSelectedTotem(context, singleplayer, "explosive Nether bed");

            PopOutcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                if (level.dimension() != Level.NETHER) {
                    throw new AssertionError("server player left Nether before bed detonation");
                }
                requireServerArmed(player, "explosive Nether bed");
                BlockState state = level.getBlockState(setup.head());
                if (!state.is(Blocks.RED_BED) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
                    throw new AssertionError("bed head disappeared before controlled detonation");
                }
                state.useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(setup.head().getCenter(), Direction.UP, setup.head(), false)
                );
                return popOutcome(player);
            });
            assertTotemPop("visible_nether_bed_preemptive_totem", outcome);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel current = (ServerLevel) player.level();
                current.setBlock(setup.head(), Blocks.AIR.defaultBlockState(), 2);
                current.setBlock(setup.foot(), Blocks.AIR.defaultBlockState(), 2);
                cleanupArmingFixture(player);
                ServerLevel returnLevel = server.getLevel(setup.returnPoint().dimension());
                if (returnLevel == null) throw new AssertionError("return dimension disappeared after bed validation");
                ReturnPoint point = setup.returnPoint();
                boolean teleported = player.teleportTo(
                    returnLevel,
                    point.x(), point.y(), point.z(), Set.of(), point.yRot(), point.xRot(), true
                );
                if (!teleported) throw new AssertionError("could not return GameTest player after Nether bed validation");
            });
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.dimension() == setup.returnPoint().dimension());
            context.waitTick();
        }
    }

    private static void armFromProductionRuntime(
        ClientGameTestContext context,
        Predicate<ThreatEvent> sourceMatcher,
        String label
    ) {
        context.runOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client player/level unavailable for " + label + " validation");
            }
            MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
            SurvivalEngine.EngineFrame frame = runtime.capture();
            ThreatEvent event = frame.timeline().events().stream()
                .filter(sourceMatcher)
                .findFirst()
                .orElseThrow(() -> new AssertionError("visible " + label + " produced no production explosion threat"));
            float unprotectedHealth = new DamageSimulator().simulate(frame.context().player(), event.damage()).after().health();
            if (unprotectedHealth > 0f) {
                throw new AssertionError(label + " fixture was not lethal; predicted health=" + unprotectedHealth
                    + " raw=" + event.damage().rawDamage());
            }
            SurvivalEngine engine = new SurvivalEngine(
                SurvivalConfig.defaults(),
                runtime,
                new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
            );
            engine.tick();
        });
    }

    private static void waitForServerSelectedTotem(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        String label
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean armed = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getInventory().getSelectedSlot() == 1
                    && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
            });
            if (armed) return;
            context.waitTick();
        }
        throw new AssertionError("Predictive Survival did not make Totem server-authoritative before " + label);
    }

    private static void prepareArmingFixture(ServerPlayer player) {
        SurvivalValidationClientGameTest.reset(player, 4f);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.STICK));
        player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.getInventory().setSelectedSlot(0);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setDeltaMovement(Vec3.ZERO);
        player.containerMenu.broadcastChanges();
    }

    private static void cleanupArmingFixture(ServerPlayer player) {
        SurvivalValidationClientGameTest.reset(player, 20f);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setDeltaMovement(Vec3.ZERO);
        player.containerMenu.broadcastChanges();
    }

    private static void requireServerArmed(ServerPlayer player, String label) {
        if (player.getInventory().getSelectedSlot() != 1 || !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            throw new AssertionError("Totem was not server-authoritative before " + label);
        }
    }

    private static PopOutcome popOutcome(ServerPlayer player) {
        return new PopOutcome(player.getHealth(), player.getMainHandItem().isEmpty());
    }

    private static void assertTotemPop(String id, PopOutcome outcome) {
        SurvivalValidationClientGameTest.assertClose(id, 1f, outcome.health(), EPSILON);
        if (!outcome.totemConsumed()) {
            throw new AssertionError(id + " did not consume the server-authoritative Totem");
        }
    }

    private record PopOutcome(float health, boolean totemConsumed) {
    }

    private record AnchorSetup(BlockPos pos, BlockState original) {
    }

    private record BedSetup(BlockPos head, BlockPos foot, ReturnPoint returnPoint) {
    }

    private record ReturnPoint(
        net.minecraft.resources.ResourceKey<Level> dimension,
        double x,
        double y,
        double z,
        float yRot,
        float xRot
    ) {
    }
}
