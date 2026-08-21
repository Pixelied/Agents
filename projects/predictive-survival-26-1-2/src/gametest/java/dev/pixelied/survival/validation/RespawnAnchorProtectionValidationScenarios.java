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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class RespawnAnchorProtectionValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final int SERVER_AUTHORITY_WAIT_TICKS = 200;

    private RespawnAnchorProtectionValidationScenarios() {
    }

    static void validateChargedAnchorArmsProtectionBeforeInteraction(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        BlockPos anchorPos = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STONE));
            player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();

            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = player.blockPosition().offset(2, 0, 0);
            level.setBlockAndUpdate(pos, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 1));
            return pos;
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)
                && minecraft.level.getBlockState(anchorPos).getValue(RespawnAnchorBlock.CHARGE) == 1
                && Math.abs(minecraft.player.getHealth() - 4f) <= EPSILON
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for Respawn Anchor validation");
                }
                var frame = new MinecraftSurvivalRuntime(minecraft).capture();
                ThreatEvent anchorThreat = frame.timeline().events().stream()
                    .filter(event -> event.id().startsWith("explosion:block:minecraft:respawn_anchor:"))
                    .filter(event -> event.damage().sourceKey().equals("minecraft:bad_respawn_point"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("charged Overworld Respawn Anchor produced no triggerable explosion threat"));
                if (anchorThreat.confidence() != Confidence.POTENTIAL || anchorThreat.impact().earliest() != 0L) {
                    throw new AssertionError("Respawn Anchor threat was not immediate POTENTIAL: " + anchorThreat);
                }
                float unprotectedHealth = new DamageSimulator()
                    .simulate(frame.context().player(), anchorThreat.damage())
                    .after()
                    .health();
                if (unprotectedHealth > 0f) {
                    throw new AssertionError("controlled Respawn Anchor fixture was not lethal before protection: health=" + unprotectedHealth);
                }
            });

            waitForServerAuthoritativeTotemSelection(context, singleplayer);

            AnchorPopObservation pop = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                if (!level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    throw new AssertionError("Respawn Anchor disappeared before deliberate interaction");
                }
                if (player.getInventory().getSelectedSlot() != 1 || !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server did not observe Totem in selected main hand before Respawn Anchor interaction");
                }
                player.invulnerableTime = 0;
                level.getBlockState(anchorPos).useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(Vec3.atCenterOf(anchorPos), Direction.UP, anchorPos, false)
                );
                return new AnchorPopObservation(
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    level.getBlockState(anchorPos).isAir()
                );
            });

            SurvivalValidationClientGameTest.assertClose("proactive_respawn_anchor_totem_pop", 1f, pop.health(), EPSILON);
            if (!pop.totemConsumed()) {
                throw new AssertionError("server-authoritative Totem was not consumed by lethal Respawn Anchor explosion");
            }
            if (!pop.anchorRemoved()) {
                throw new AssertionError("vanilla Respawn Anchor explosion did not remove the anchor block");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ((ServerLevel) player.level()).removeBlock(anchorPos, false);
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            });
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
        throw new AssertionError("Predictive Survival did not make Totem server-authoritative before deliberate Respawn Anchor interaction");
    }

    private record AnchorPopObservation(float health, boolean totemConsumed, boolean anchorRemoved) {
    }
}
