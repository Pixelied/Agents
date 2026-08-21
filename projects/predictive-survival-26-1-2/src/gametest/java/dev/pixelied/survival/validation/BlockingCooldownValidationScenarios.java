package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.MinecraftInventorySnapshotFactory;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.planner.SurvivalCandidateGenerator;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class BlockingCooldownValidationScenarios {
    private BlockingCooldownValidationScenarios() {
    }

    static void validateServerCooldownMakesShieldUnavailable(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 5f);
            player.getInventory().setSelectedSlot(0);
            ItemStack shield = new ItemStack(Items.SHIELD);
            player.getInventory().setItem(0, shield);
            player.containerMenu.broadcastChanges();
            player.getCooldowns().addCooldown(shield, 80);
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getMainHandItem().is(Items.SHIELD)
            && minecraft.player.getCooldowns().isOnCooldown(minecraft.player.getMainHandItem()));

        context.runOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable");
            MinecraftInventorySnapshotFactory factory = new MinecraftInventorySnapshotFactory();
            var inventory = factory.captureInventory(minecraft.player);
            var selected = inventory.slot(inventory.selectedHotbarIndex()).orElseThrow();
            if (!selected.blockingOnCooldown()) {
                throw new AssertionError("server item cooldown was not captured in the blocking inventory snapshot");
            }
            var candidates = new SurvivalCandidateGenerator().generate(
                context(), timeline(), inventory, factory.captureMenu(minecraft.player)
            );
            if (candidates.stream().anyMatch(SurvivalAction.RaiseShield.class::isInstance)) {
                throw new AssertionError("cooling shield produced an impossible RaiseShield candidate");
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setSelectedSlot(0);
            player.containerMenu.broadcastChanges();
        });
        context.waitTick();
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:blockable"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "blockable", ThreatKind.OTHER, new TickWindow(8, 8), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, false
        )));
    }
}
