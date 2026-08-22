package dev.pixelied.survival.validation;

import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.MinecraftEquipmentAdapter;
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

final class DeathProtectionComponentValidationScenarios {
    private DeathProtectionComponentValidationScenarios() {
    }

    static void validateClientObservableCustomEffects(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        ItemStack deterministic = new ItemStack(Items.STICK);
        deterministic.set(DataComponents.DEATH_PROTECTION, new DeathProtection(List.of(
            new ClearAllStatusEffectsConsumeEffect(),
            new ApplyStatusEffectsConsumeEffect(List.of(
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0),
                new MobEffectInstance(MobEffects.REGENERATION, 60, 1)
            ))
        )));

        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, deterministic.copy());
            player.containerMenu.broadcastChanges();
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getMainHandItem().get(DataComponents.DEATH_PROTECTION) != null);

        DeathProtectionSnapshot.ProtectionItem captured = context.computeOnClient(minecraft ->
            new MinecraftEquipmentAdapter().deathProtection(minecraft.player).mainHand().orElseThrow()
        );

        if (captured.outcomeUncertain()) {
            throw new AssertionError("deterministic custom DEATH_PROTECTION was collapsed to an uncertain generic outcome");
        }
        if (!captured.clearExistingEffects()) {
            throw new AssertionError("deterministic custom DEATH_PROTECTION lost ClearAllStatusEffects");
        }
        if (captured.effects().size() != 2) {
            throw new AssertionError("deterministic custom DEATH_PROTECTION effects were discarded: " + captured);
        }
        var fireResistance = captured.effects().get(0);
        var regeneration = captured.effects().get(1);
        if (!"minecraft:fire_resistance".equals(fireResistance.effectKey())
            || fireResistance.durationTicks() != 120
            || fireResistance.amplifier() != 0) {
            throw new AssertionError("custom Fire Resistance effect mismatch: " + fireResistance);
        }
        if (!"minecraft:regeneration".equals(regeneration.effectKey())
            || regeneration.durationTicks() != 60
            || regeneration.amplifier() != 1) {
            throw new AssertionError("custom Regeneration effect mismatch: " + regeneration);
        }

        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
        context.waitFor(minecraft -> minecraft.player != null && minecraft.player.getMainHandItem().isEmpty());

        RoutedDeathProtectionValidationScenarios.validateRoutedCustomProtectionPreservesObservableSemantics(
            context,
            singleplayer
        );
    }
}
