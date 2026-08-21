package dev.pixelied.survival.validation;

import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.MinecraftEquipmentAdapter;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;

import java.util.List;

final class CustomDeathProtectionValidationScenarios {
    private CustomDeathProtectionValidationScenarios() {
    }

    static void validateCustomDeathProtectionComponents(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        DeathProtection deterministic = new DeathProtection(List.of(
            new ClearAllStatusEffectsConsumeEffect(),
            new ApplyStatusEffectsConsumeEffect(List.of(
                new MobEffectInstance(MobEffects.REGENERATION, 80, 2),
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0)
            ))
        ));
        setServerSelectedStack(singleplayer, deterministic);
        context.waitFor(minecraft -> {
            if (minecraft.player == null) return false;
            return new MinecraftEquipmentAdapter()
                .deathProtection(minecraft.player)
                .mainHand()
                .map(item -> item.clearExistingEffects() && !item.outcomeUncertain())
                .orElse(false);
        });

        context.runOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable");
            DeathProtectionSnapshot.ProtectionItem item = new MinecraftEquipmentAdapter()
                .deathProtection(minecraft.player)
                .mainHand()
                .orElseThrow(() -> new AssertionError("custom deterministic DEATH_PROTECTION was not captured"));
            if (item.outcomeUncertain()) {
                throw new AssertionError("deterministic custom DEATH_PROTECTION was marked uncertain");
            }
            if (!item.clearExistingEffects()) {
                throw new AssertionError("custom clear-all death effect was not captured");
            }
            assertEffect(item.effects(), "minecraft:regeneration", 80, 2);
            assertEffect(item.effects(), "minecraft:fire_resistance", 120, 0);
            if (item.effects().size() != 2) {
                throw new AssertionError("expected exactly two deterministic custom death effects, got " + item.effects());
            }
        });

        DeathProtection probabilistic = new DeathProtection(List.of(
            new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), 0.5f)
        ));
        setServerSelectedStack(singleplayer, probabilistic);
        context.waitFor(minecraft -> {
            if (minecraft.player == null) return false;
            return new MinecraftEquipmentAdapter()
                .deathProtection(minecraft.player)
                .mainHand()
                .map(DeathProtectionSnapshot.ProtectionItem::outcomeUncertain)
                .orElse(false);
        });

        context.runOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable");
            DeathProtectionSnapshot.ProtectionItem item = new MinecraftEquipmentAdapter()
                .deathProtection(minecraft.player)
                .mainHand()
                .orElseThrow(() -> new AssertionError("custom probabilistic DEATH_PROTECTION was not captured"));
            if (!item.outcomeUncertain()) {
                throw new AssertionError("probabilistic custom DEATH_PROTECTION must fail closed as uncertain");
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
        context.waitFor(minecraft -> minecraft.player != null && minecraft.player.getMainHandItem().isEmpty());
    }

    private static void setServerSelectedStack(TestSingleplayerContext singleplayer, DeathProtection protection) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.getInventory().setSelectedSlot(0);
            ItemStack stack = new ItemStack(Items.STICK);
            stack.set(DataComponents.DEATH_PROTECTION, protection);
            player.getInventory().setItem(0, stack);
            player.containerMenu.broadcastChanges();
        });
    }

    private static void assertEffect(
        List<EffectInstanceSnapshot> effects,
        String key,
        int duration,
        int amplifier
    ) {
        EffectInstanceSnapshot effect = effects.stream()
            .filter(candidate -> candidate.effectKey().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing custom death effect " + key + " from " + effects));
        if (effect.durationTicks() != duration || effect.amplifier() != amplifier) {
            throw new AssertionError(
                key + " expected duration=" + duration + " amplifier=" + amplifier
                    + " actual=" + effect.durationTicks() + "/" + effect.amplifier()
            );
        }
    }
}
