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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RoutedDeathProtectionValidationScenarios {
    private RoutedDeathProtectionValidationScenarios() {
    }

    static void validateRoutedCustomProtectionPreservesObservableSemantics(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        DeathProtection deterministic = new DeathProtection(List.of(
            new ClearAllStatusEffectsConsumeEffect(),
            new ApplyStatusEffectsConsumeEffect(List.of(
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0)
            ))
        ));

        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 5f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STICK));
            ItemStack custom = new ItemStack(Items.BLAZE_ROD);
            custom.set(DataComponents.DEATH_PROTECTION, deterministic);
            player.getInventory().setItem(1, custom);
            player.containerMenu.broadcastChanges();
        });

        context.waitFor(minecraft -> minecraft.player != null
            && deterministic.equals(minecraft.player.getInventory().getItem(1).get(DataComponents.DEATH_PROTECTION)));

        context.runOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable");
            MinecraftInventorySnapshotFactory factory = new MinecraftInventorySnapshotFactory();
            var candidates = new SurvivalCandidateGenerator().generate(
                context(),
                timeline(),
                factory.captureInventory(minecraft.player),
                factory.captureMenu(minecraft.player)
            );
            SurvivalAction.EquipDeathProtection action = candidates.stream()
                .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                .map(SurvivalAction.EquipDeathProtection.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no death-protection candidate for routed custom component"));
            if (action.item().outcomeUncertain()) {
                throw new AssertionError("routed deterministic custom protection was downgraded to generic uncertainty");
            }
            if (!action.item().clearExistingEffects()
                || action.item().effects().size() != 1
                || !"minecraft:fire_resistance".equals(action.item().effects().getFirst().effectKey())) {
                throw new AssertionError("routed custom protection lost observable effect semantics: " + action.item());
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setItem(1, ItemStack.EMPTY);
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
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:routed_custom"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "routed-custom", ThreatKind.OTHER, new TickWindow(3, 3), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, false, true, false
        )));
    }
}
