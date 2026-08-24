package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageResult;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ExperimentalValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private ExperimentalValidationScenarios() {
    }

    static List<ValidationResult> runtimeSlice(TestSingleplayerContext singleplayer) {
        List<ValidationResult> results = new ArrayList<>();
        results.add(validateSmallPrecursorIsNotImmunity(singleplayer));
        results.add(validatePearlFeatherFallingCooldownAdvantage(singleplayer));
        return List.copyOf(results);
    }

    private static ValidationResult validateSmallPrecursorIsNotImmunity(TestSingleplayerContext singleplayer) {
        DamageResult predictedFirst = SIMULATOR.simulate(cleanSnapshot(20f, MitigationSnapshot.none()), generic(1f));
        DamageResult predictedSecond = SIMULATOR.simulate(predictedFirst.after(), generic(15f));

        float actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            ServerLevel level = (ServerLevel) player.level();
            DamageSource generic = player.damageSources().generic();
            player.hurtServer(level, generic, 1f);
            player.hurtServer(level, generic, 15f);
            return player.getHealth();
        });

        SurvivalValidationClientGameTest.assertClose(
            "small_precursor_total_damage",
            5f,
            actual,
            EPSILON
        );
        return new ValidationResult(
            "experimental_small_precursor_not_immunity",
            predictedSecond.after().health(),
            actual,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
        );
    }

    private static ValidationResult validatePearlFeatherFallingCooldownAdvantage(TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();

            SurvivalValidationClientGameTest.reset(player, 20f);
            equipFeatherFallingBoots(player, level);
            DamageSource genericSource = player.damageSources().generic();
            DamageSource pearlSource = player.damageSources().enderPearl();

            MitigationSnapshot genericMitigation = mitigation(player, level, genericSource);
            MitigationSnapshot pearlMitigation = mitigation(player, level, pearlSource);
            if (pearlMitigation.enchantmentProtection() <= genericMitigation.enchantmentProtection()) {
                throw new AssertionError(
                    "Feather Falling did not provide additional ender-pearl protection: pearl="
                        + pearlMitigation.enchantmentProtection()
                        + " generic=" + genericMitigation.enchantmentProtection()
                );
            }

            float predictedBaseline = SIMULATOR.simulate(
                cleanSnapshot(20f, genericMitigation),
                generic(9f)
            ).after().health();
            player.hurtServer(level, genericSource, 9f);
            float actualBaseline = player.getHealth();
            SurvivalValidationClientGameTest.assertClose(
                "experimental_pearl_ff_baseline",
                predictedBaseline,
                actualBaseline,
                EPSILON
            );

            SurvivalValidationClientGameTest.reset(player, 20f);
            equipFeatherFallingBoots(player, level);
            genericSource = player.damageSources().generic();
            pearlSource = player.damageSources().enderPearl();
            genericMitigation = mitigation(player, level, genericSource);
            pearlMitigation = mitigation(player, level, pearlSource);

            DamageResult predictedPearl = SIMULATOR.simulate(
                cleanSnapshot(20f, pearlMitigation),
                enderPearl(5f)
            );
            PlayerSnapshot followUpInput = withMitigation(predictedPearl.after(), genericMitigation);
            DamageResult predictedFollowUp = SIMULATOR.simulate(followUpInput, generic(9f));

            player.hurtServer(level, pearlSource, 5f);
            float healthAfterPearl = player.getHealth();
            player.hurtServer(level, genericSource, 9f);
            float actualManipulated = player.getHealth();

            SurvivalValidationClientGameTest.assertClose(
                "experimental_pearl_ff_after_pearl",
                predictedPearl.after().health(),
                healthAfterPearl,
                EPSILON
            );
            if (!(healthAfterPearl > 15f)) {
                throw new AssertionError(
                    "Feather Falling failed to reduce actual 5-raw pearl health loss: health=" + healthAfterPearl
                );
            }
            if (!(actualManipulated > actualBaseline + EPSILON)) {
                throw new AssertionError(
                    "pearl hurt-cooldown sequence was not beneficial: manipulated=" + actualManipulated
                        + " baseline=" + actualBaseline
                );
            }

            return new ValidationResult(
                "experimental_pearl_feather_falling_hurt_cooldown",
                predictedFollowUp.after().health(),
                actualManipulated,
                ValidationStatus.EXPERIMENTAL,
                EPSILON
            );
        });
    }

    private static void equipFeatherFallingBoots(ServerPlayer player, ServerLevel level) {
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        var featherFalling = level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.FEATHER_FALLING);
        boots.enchant(featherFalling, 4);
        player.setItemSlot(EquipmentSlot.FEET, boots);
    }

    private static MitigationSnapshot mitigation(
        ServerPlayer player,
        ServerLevel level,
        DamageSource source
    ) {
        float protection = EnchantmentHelper.getDamageProtection(level, player, source);
        int protectionPoints = Math.round(protection);
        if (Math.abs(protection - protectionPoints) > EPSILON) {
            throw new AssertionError("non-integral enchantment protection=" + protection + " for " + source);
        }
        return new MitigationSnapshot(
            player.getArmorValue(),
            (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
            1f,
            protectionPoints,
            false,
            0
        );
    }

    private static DamageSourceSnapshot generic(float rawDamage) {
        return new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "minecraft:generic"
        );
    }

    private static DamageSourceSnapshot enderPearl(float rawDamage) {
        return new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.IS_FALL),
            false,
            1f,
            false,
            Optional.empty(),
            "minecraft:ender_pearl"
        );
    }

    private static PlayerSnapshot cleanSnapshot(float health, MitigationSnapshot mitigation) {
        return new PlayerSnapshot(
            health,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            mitigation,
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }

    private static PlayerSnapshot withMitigation(PlayerSnapshot player, MitigationSnapshot mitigation) {
        return new PlayerSnapshot(
            player.health(),
            player.absorption(),
            player.playerInvulnerable(),
            player.abilityInvulnerable(),
            player.deadOrDying(),
            player.difficulty(),
            mitigation,
            player.statusEffects(),
            player.blocking(),
            player.hurtState(),
            player.deathProtection(),
            player.boundingBox(),
            player.position(),
            player.velocity(),
            player.equipmentItemKeys(),
            player.stateProperties()
        );
    }
}
