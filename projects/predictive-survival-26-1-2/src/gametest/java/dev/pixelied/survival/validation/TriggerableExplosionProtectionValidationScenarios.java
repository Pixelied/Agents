package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class TriggerableExplosionProtectionValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private TriggerableExplosionProtectionValidationScenarios() {
    }

    static void validateVisibleLethalCrystalArmsProtectionBeforeDetonation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int crystalId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STONE));
            player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();

            ServerLevel level = (ServerLevel) player.level();
            EndCrystal crystal = new EndCrystal(level, player.getX() + 2.0d, player.getY() + 1.0d, player.getZ());
            crystal.setShowBottom(false);
            if (!level.addFreshEntity(crystal)) {
                throw new AssertionError("failed to add End Crystal for proactive-protection validation");
            }
            return crystal.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.getEntity(crystalId) instanceof EndCrystal
                && Math.abs(minecraft.player.getHealth() - 4f) <= EPSILON
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for End Crystal validation");
                }
                var frame = new MinecraftSurvivalRuntime(minecraft).capture();
                ThreatEvent crystalThreat = frame.timeline().events().stream()
                    .filter(event -> event.id().equals("explosion:" + crystalId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("visible End Crystal produced no triggerable explosion threat"));
                if (crystalThreat.confidence() != Confidence.POTENTIAL || crystalThreat.impact().earliest() != 0L) {
                    throw new AssertionError("End Crystal threat was not modeled as immediate POTENTIAL: " + crystalThreat);
                }
                float unprotectedHealth = new DamageSimulator()
                    .simulate(frame.context().player(), crystalThreat.damage())
                    .after()
                    .health();
                if (unprotectedHealth > 0f) {
                    throw new AssertionError("controlled End Crystal fixture was not lethal before protection: health=" + unprotectedHealth);
                }
            });

            waitForServerAuthoritativeTotemSelection(context, singleplayer);

            CrystalPopObservation pop = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                Entity entity = level.getEntity(crystalId);
                if (!(entity instanceof EndCrystal crystal)) {
                    throw new AssertionError("End Crystal disappeared before deliberate detonation");
                }
                if (player.getInventory().getSelectedSlot() != 1 || !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server did not observe Totem in selected main hand before crystal detonation");
                }
                player.invulnerableTime = 0;
                boolean destroyed = crystal.hurtServer(level, player.damageSources().generic(), 1f);
                return new CrystalPopObservation(
                    destroyed,
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    crystal.isRemoved()
                );
            });

            if (!pop.destroyed() || !pop.crystalRemoved()) {
                throw new AssertionError("vanilla End Crystal detonation path did not remove the crystal");
            }
            SurvivalValidationClientGameTest.assertClose("proactive_end_crystal_totem_pop", 1f, pop.health(), EPSILON);
            if (!pop.totemConsumed()) {
                throw new AssertionError("server-authoritative Totem was not consumed by lethal End Crystal detonation");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity crystal = player.level().getEntity(crystalId);
                if (crystal != null) crystal.discard();
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
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean selected = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getInventory().getSelectedSlot() == 1
                    && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
            });
            if (selected) return;
            context.waitTick();
        }
        throw new AssertionError("Predictive Survival did not make Totem server-authoritative before deliberate End Crystal detonation");
    }

    private record CrystalPopObservation(
        boolean destroyed,
        float health,
        boolean totemConsumed,
        boolean crystalRemoved
    ) {
    }
}
