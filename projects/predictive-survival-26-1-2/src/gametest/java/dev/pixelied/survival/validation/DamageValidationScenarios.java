package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageResult;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DamageValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private DamageValidationScenarios() {
    }

    static List<ValidationResult> firstRuntimeSlice(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        List<ValidationResult> results = new ArrayList<>();
        results.add(validatePlayerAttack(singleplayer));
        results.add(validateArmorResistanceProtection(singleplayer));
        results.add(validateHurtCooldownFollowUp(singleplayer, 3f, "hurt_cooldown_smaller"));
        results.add(validateHurtCooldownFollowUp(singleplayer, 5f, "hurt_cooldown_equal"));
        results.add(validateHurtCooldownFollowUp(singleplayer, 8f, "hurt_cooldown_larger"));
        results.add(validateShieldTiming(context, singleplayer, 4));
        results.add(validateShieldTiming(context, singleplayer, 5));
        return List.copyOf(results);
    }

    private static ValidationResult validatePlayerAttack(TestSingleplayerContext singleplayer) {
        DamageSourceSnapshot predictedSource = source(6f, "minecraft:player_attack");
        float predicted = SIMULATOR.simulate(cleanSnapshot(20f), predictedSource).after().health();

        float actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            ServerLevel level = (ServerLevel) player.level();
            player.hurtServer(level, player.damageSources().playerAttack(player), 6f);
            return player.getHealth();
        });

        return result("player_attack_raw_6", predicted, actual);
    }

    private static ValidationResult validateArmorResistanceProtection(TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            ServerLevel level = (ServerLevel) player.level();

            ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
            var protection = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PROTECTION);
            chestplate.enchant(protection, 4);
            player.setItemSlot(EquipmentSlot.CHEST, chestplate);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));

            DamageSource actualSource = player.damageSources().generic();
            float enchantmentProtection = EnchantmentHelper.getDamageProtection(level, player, actualSource);
            int protectionPoints = Math.round(enchantmentProtection);
            if (Math.abs(enchantmentProtection - protectionPoints) > EPSILON) {
                throw new AssertionError("generic Protection IV produced non-integral protection=" + enchantmentProtection);
            }

            MitigationSnapshot mitigation = new MitigationSnapshot(
                player.getArmorValue(),
                (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                1f,
                protectionPoints,
                false,
                0
            );
            StatusEffectsSnapshot effects = new StatusEffectsSnapshot(false, 0);
            float predicted = SIMULATOR.simulate(
                cleanSnapshot(20f, mitigation, effects, BlockingSnapshot.none(), HurtState.unknown()),
                source(10f, "minecraft:generic")
            ).after().health();

            player.hurtServer(level, actualSource, 10f);
            return result("armor_resistance_protection_raw_10", predicted, player.getHealth());
        });
    }

    private static ValidationResult validateHurtCooldownFollowUp(
        TestSingleplayerContext singleplayer,
        float followUp,
        String id
    ) {
        DamageSourceSnapshot firstSource = source(5f, "minecraft:generic");
        DamageResult first = SIMULATOR.simulate(cleanSnapshot(20f), firstSource);
        DamageResult second = SIMULATOR.simulate(first.after(), source(followUp, "minecraft:generic"));
        float predicted = second.after().health();

        float actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            ServerLevel level = (ServerLevel) player.level();
            DamageSource source = player.damageSources().generic();
            player.hurtServer(level, source, 5f);
            player.hurtServer(level, source, followUp);
            return player.getHealth();
        });

        return result(id, predicted, actual);
    }

    private static ValidationResult validateShieldTiming(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int elapsedUseTicks
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        });
        context.waitFor(minecraft -> minecraft.player != null && minecraft.player.getOffhandItem().is(Items.SHIELD));
        context.getInput().lookAt(0f, 0f);
        context.getInput().holdKey(options -> options.keyUse);

        try {
            waitForShieldUseTicks(context, singleplayer, elapsedUseTicks);
            return singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                int observedUseTicks = player.getTicksUsingItem();
                if (observedUseTicks != elapsedUseTicks) {
                    throw new AssertionError(
                        "shield timing expected " + elapsedUseTicks + " use ticks but server observed " + observedUseTicks
                    );
                }

                ServerLevel level = (ServerLevel) player.level();
                var playerAttackType = level.registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.PLAYER_ATTACK);
                DamageSource actualSource = new DamageSource(playerAttackType, player.position().add(0d, 0d, 5d));

                BlockingSnapshot blocking = new BlockingSnapshot(true, 1f, elapsedUseTicks, 5);
                float predicted = SIMULATOR.simulate(
                    cleanSnapshot(20f, MitigationSnapshot.none(), StatusEffectsSnapshot.none(), blocking, HurtState.unknown()),
                    source(6f, "minecraft:player_attack")
                ).after().health();

                player.hurtServer(level, actualSource, 6f);
                return result("shield_use_ticks_" + elapsedUseTicks, predicted, player.getHealth());
            });
        } finally {
            context.getInput().releaseKey(options -> options.keyUse);
            context.waitTick();
        }
    }

    private static void waitForShieldUseTicks(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int expectedUseTicks
    ) {
        for (int wait = 0; wait < ClientGameTestContext.DEFAULT_TIMEOUT; wait++) {
            int observed = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.isUsingItem() ? player.getTicksUsingItem() : -1;
            });
            if (observed == expectedUseTicks) return;
            if (observed > expectedUseTicks) {
                throw new AssertionError(
                    "shield use skipped target " + expectedUseTicks + " and reached " + observed
                );
            }
            context.waitTick();
        }
        throw new AssertionError("shield never reached " + expectedUseTicks + " authoritative use ticks");
    }

    private static DamageSourceSnapshot source(float rawDamage, String sourceKey) {
        return new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            sourceKey
        );
    }

    private static PlayerSnapshot cleanSnapshot(float health) {
        return cleanSnapshot(
            health,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown()
        );
    }

    private static PlayerSnapshot cleanSnapshot(
        float health,
        MitigationSnapshot mitigation,
        StatusEffectsSnapshot effects,
        BlockingSnapshot blocking,
        HurtState hurtState
    ) {
        return new PlayerSnapshot(
            health,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            mitigation,
            effects,
            blocking,
            hurtState,
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }

    private static ValidationResult result(String id, float predicted, float actual) {
        return new ValidationResult(id, predicted, actual, ValidationStatus.RUNTIME_CONFIRMED, EPSILON);
    }
}
