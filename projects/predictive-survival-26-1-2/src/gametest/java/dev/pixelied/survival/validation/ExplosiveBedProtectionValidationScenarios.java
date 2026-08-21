package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.timeline.ThreatEvent;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

final class ExplosiveBedProtectionValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final int SERVER_AUTHORITY_WAIT_TICKS = 200;

    private ExplosiveBedProtectionValidationScenarios() {
    }

    static void validateNetherBedArmsProtectionBeforeInteraction(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        BedFixture fixture = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (overworld == null || nether == null) throw new AssertionError("required vanilla dimensions unavailable");
            Vec3 returnPosition = player.position();

            BlockPos origin = new BlockPos(192, 96, 192);
            for (int x = -4; x <= 5; x++) {
                for (int z = -4; z <= 4; z++) {
                    nether.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.OBSIDIAN.defaultBlockState());
                    for (int y = 0; y <= 4; y++) {
                        nether.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }

            boolean teleported = player.teleportTo(
                nether,
                origin.getX() + 0.5d,
                origin.getY(),
                origin.getZ() + 0.5d,
                Set.of(),
                0f,
                0f,
                true
            );
            if (!teleported) throw new AssertionError("failed to teleport validation player to Nether");

            SurvivalValidationClientGameTest.reset(player, 4f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STONE));
            player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();

            Direction facing = Direction.EAST;
            BlockPos footPos = origin.offset(2, 0, 0);
            BlockPos headPos = footPos.relative(facing);
            nether.setBlockAndUpdate(
                footPos,
                Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, facing)
                    .setValue(BedBlock.PART, BedPart.FOOT)
                    .setValue(BedBlock.OCCUPIED, false)
            );
            nether.setBlockAndUpdate(
                headPos,
                Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, facing)
                    .setValue(BedBlock.PART, BedPart.HEAD)
                    .setValue(BedBlock.OCCUPIED, false)
            );
            return new BedFixture(footPos, headPos, returnPosition);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.dimension() == Level.NETHER
                && minecraft.level.getBlockState(fixture.headPos()).is(Blocks.RED_BED)
                && Math.abs(minecraft.player.getHealth() - 4f) <= EPSILON
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for explosive-bed validation");
                }
                var frame = new MinecraftSurvivalRuntime(minecraft).capture();
                ThreatEvent bedThreat = frame.timeline().events().stream()
                    .filter(event -> event.id().startsWith("explosion:block:minecraft:red_bed:"))
                    .filter(event -> event.damage().sourceKey().equals("minecraft:bad_respawn_point"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("real Nether bed produced no triggerable explosion threat"));
                if (bedThreat.confidence() != Confidence.POTENTIAL || bedThreat.impact().earliest() != 0L) {
                    throw new AssertionError("explosive bed threat was not immediate POTENTIAL: " + bedThreat);
                }
                float unprotectedHealth = new DamageSimulator()
                    .simulate(frame.context().player(), bedThreat.damage())
                    .after()
                    .health();
                if (unprotectedHealth > 0f) {
                    throw new AssertionError("controlled Nether bed fixture was not lethal before protection: health=" + unprotectedHealth);
                }
            });

            waitForServerAuthoritativeTotemSelection(context, singleplayer);

            BedPopObservation pop = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel nether = server.getLevel(Level.NETHER);
                if (nether == null) throw new AssertionError("Nether disappeared before bed interaction");
                if (player.level() != nether) throw new AssertionError("player left Nether before bed interaction");
                if (player.getInventory().getSelectedSlot() != 1 || !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server did not observe Totem in selected main hand before bed interaction");
                }
                player.invulnerableTime = 0;
                nether.getBlockState(fixture.headPos()).useWithoutItem(
                    nether,
                    player,
                    new BlockHitResult(Vec3.atCenterOf(fixture.headPos()), Direction.UP, fixture.headPos(), false)
                );
                return new BedPopObservation(
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    nether.getBlockState(fixture.headPos()).isAir() && nether.getBlockState(fixture.footPos()).isAir()
                );
            });

            SurvivalValidationClientGameTest.assertClose("proactive_explosive_bed_totem_pop", 1f, pop.health(), EPSILON);
            if (!pop.totemConsumed()) {
                throw new AssertionError("server-authoritative Totem was not consumed by lethal Nether bed explosion");
            }
            if (!pop.bedRemoved()) {
                throw new AssertionError("vanilla explosive-bed interaction did not remove both bed halves");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel nether = server.getLevel(Level.NETHER);
                ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                if (nether != null) {
                    nether.removeBlock(fixture.headPos(), false);
                    nether.removeBlock(fixture.footPos(), false);
                }
                if (overworld != null && player.level() != overworld) {
                    player.teleportTo(
                        overworld,
                        fixture.returnPosition().x(),
                        fixture.returnPosition().y(),
                        fixture.returnPosition().z(),
                        Set.of(),
                        0f,
                        0f,
                        true
                    );
                }
                SurvivalValidationClientGameTest.reset(player, 20f);
            });
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD);
            context.waitTick();
        }
    }

    private static void waitForServerAuthoritativeTotemSelection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < SERVER_AUTHORITY_WAIT_TICKS; tick++) {
            boolean selected = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getInventory().getSelectedSlot() == 1
                    && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
            });
            if (selected) return;
            context.waitTick();
        }
        throw new AssertionError("Predictive Survival did not make Totem server-authoritative before deliberate Nether bed interaction");
    }

    private record BedFixture(BlockPos footPos, BlockPos headPos, Vec3 returnPosition) {
    }

    private record BedPopObservation(float health, boolean totemConsumed, boolean bedRemoved) {
    }
}
