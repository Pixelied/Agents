package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.damage.DamageResult;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.MinecraftDamageAdapter;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ExtendedValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final DamageSimulator SIMULATOR = new DamageSimulator();
    private static final MinecraftDamageAdapter DAMAGE_ADAPTER = new MinecraftDamageAdapter();

    private ExtendedValidationScenarios() {
    }

    static List<ValidationResult> runtimeSlice(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        List<ValidationResult> results = new ArrayList<>();
        for (SourceCase sourceCase : SourceCase.values()) {
            results.add(validateSourceCase(context, singleplayer, sourceCase));
        }
        results.add(validateRepeatedDeathProtection(singleplayer));
        return List.copyOf(results);
    }

    private static ValidationResult validateSourceCase(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        SourceCase sourceCase
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
        });
        context.waitTick();

        float predicted = context.computeOnClient(minecraft -> {
            LocalPlayer player = minecraft.player;
            if (player == null) throw new AssertionError("client player unavailable for " + sourceCase.id);
            PlayerSnapshot snapshot = new MinecraftSnapshotFactory().capture(player);
            DamageSourceSnapshot source = DAMAGE_ADAPTER.snapshot(
                sourceCase.source(player), sourceCase.rawDamage, player
            );
            return SIMULATOR.simulate(snapshot, source).after().health();
        });

        float actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            player.hurtServer(level, sourceCase.source(player), sourceCase.rawDamage);
            return player.getHealth();
        });

        return new ValidationResult(
            sourceCase.id,
            predicted,
            actual,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
        );
    }

    private static ValidationResult validateRepeatedDeathProtection(TestSingleplayerContext singleplayer) {
        DamageSourceSnapshot firstSource = generic(20f);
        DamageSourceSnapshot secondSource = generic(30f);
        PlayerSnapshot initial = new dev.pixelied.survival.core.PlayerSnapshot(
            4f,
            0f,
            false,
            false,
            false,
            dev.pixelied.survival.core.DifficultySnapshot.NORMAL,
            dev.pixelied.survival.damage.MitigationSnapshot.none(),
            dev.pixelied.survival.damage.StatusEffectsSnapshot.none(),
            dev.pixelied.survival.damage.BlockingSnapshot.none(),
            dev.pixelied.survival.damage.HurtState.unknown(),
            DeathProtectionSnapshot.both(
                DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
                DeathProtectionSnapshot.ProtectionItem.vanillaTotem()
            ),
            new dev.pixelied.survival.core.AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new dev.pixelied.survival.core.Vec3Snapshot(0, 0, 0),
            new dev.pixelied.survival.core.Vec3Snapshot(0, 0, 0),
            java.util.Map.of()
        );
        DamageResult first = SIMULATOR.simulate(initial, firstSource);
        if (!first.deathProtectionConsumed()) {
            throw new AssertionError("first predicted lethal hit did not consume death protection");
        }
        DamageResult second = SIMULATOR.simulate(first.after(), secondSource);
        if (!second.deathProtectionConsumed()) {
            throw new AssertionError("second predicted lethal follow-up did not consume remaining death protection");
        }

        PopChainState actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.setMainHandItem(new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            ServerLevel level = (ServerLevel) player.level();
            DamageSource source = player.damageSources().generic();
            player.hurtServer(level, source, 20f);
            player.hurtServer(level, source, 30f);
            return new PopChainState(
                player.getHealth(),
                player.getMainHandItem().isEmpty(),
                player.getOffhandItem().isEmpty()
            );
        });

        if (!actual.mainConsumed || !actual.offConsumed) {
            throw new AssertionError(
                "repeated death-protection chain did not consume both hands: main="
                    + actual.mainConsumed + " off=" + actual.offConsumed
            );
        }

        return new ValidationResult(
            "death_protection_repeated_follow_up",
            second.after().health(),
            actual.health,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
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

    private enum SourceCase {
        FALL("fall_raw_6", 6f) {
            @Override DamageSource source(Player player) { return player.damageSources().fall(); }
        },
        ENDER_PEARL("ender_pearl_raw_5", 5f) {
            @Override DamageSource source(Player player) { return player.damageSources().enderPearl(); }
        },
        WIND_CHARGE("wind_charge_raw_1", 1f) {
            @Override DamageSource source(Player player) { return player.damageSources().windCharge(player, player); }
        },
        MACE("mace_smash_raw_10", 10f) {
            @Override DamageSource source(Player player) { return player.damageSources().mace(player); }
        },
        LAVA("lava_raw_4", 4f) {
            @Override DamageSource source(Player player) { return player.damageSources().lava(); }
        },
        ON_FIRE("on_fire_raw_1", 1f) {
            @Override DamageSource source(Player player) { return player.damageSources().onFire(); }
        },
        DROWNING("drowning_raw_2", 2f) {
            @Override DamageSource source(Player player) { return player.damageSources().drown(); }
        },
        FREEZING("freezing_raw_1", 1f) {
            @Override DamageSource source(Player player) { return player.damageSources().freeze(); }
        },
        WITHER("wither_raw_1", 1f) {
            @Override DamageSource source(Player player) { return player.damageSources().wither(); }
        },
        THORNS("thorns_raw_2", 2f) {
            @Override DamageSource source(Player player) { return player.damageSources().thorns(player); }
        },
        SUFFOCATION("in_wall_raw_1", 1f) {
            @Override DamageSource source(Player player) { return player.damageSources().inWall(); }
        },
        STARVATION("starve_raw_1", 1f) {
            @Override DamageSource source(Player player) { return player.damageSources().starve(); }
        };

        final String id;
        final float rawDamage;

        SourceCase(String id, float rawDamage) {
            this.id = id;
            this.rawDamage = rawDamage;
        }

        abstract DamageSource source(Player player);
    }

    private record PopChainState(float health, boolean mainConsumed, boolean offConsumed) {
    }
}
