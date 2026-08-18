package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SurvivalValidationClientGameTest implements FabricClientGameTest {
    private static final float EPSILON = 0.0001f;

    @Override
    public void runTest(ClientGameTestContext context) {
        List<ValidationResult> results = new ArrayList<>();
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(minecraft -> minecraft.player != null && minecraft.level != null);

            results.add(validateGenericDamage(singleplayer));
            validateHurtCooldown(singleplayer);
            validateDeathProtection(singleplayer, InteractionHand.MAIN_HAND);
            validateDeathProtection(singleplayer, InteractionHand.OFF_HAND);

            for (ValidationResult result : results) {
                if (!result.passes()) {
                    throw new AssertionError(
                        result.id() + " predicted=" + result.predictedHealth()
                            + " actual=" + result.actualHealth()
                            + " tolerance=" + result.tolerance()
                    );
                }
            }
        }
    }

    private static ValidationResult validateGenericDamage(TestSingleplayerContext singleplayer) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(4f), Set.of(), false, 1f, false, Optional.empty(), "minecraft:generic"
        );
        float predicted = new DamageSimulator().simulate(cleanSnapshot(20f), source).after().health();

        float actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = onlyPlayer(server);
            reset(player, 20f);
            player.hurtServer((ServerLevel) player.level(), player.damageSources().generic(), 4f);
            return player.getHealth();
        });

        return new ValidationResult(
            "generic_raw_4",
            predicted,
            actual,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
        );
    }

    private static void validateHurtCooldown(TestSingleplayerContext singleplayer) {
        CooldownTrace trace = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = onlyPlayer(server);
            reset(player, 20f);
            ServerLevel level = (ServerLevel) player.level();
            boolean abilityInvulnerable = player.getAbilities().invulnerable;
            int beforeInvulnerability = player.invulnerableTime;
            float armor = player.getArmorValue();
            double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float absorption = player.getAbsorptionAmount();
            int activeEffects = player.getActiveEffects().size();
            boolean usingItem = player.isUsingItem();
            String useItem = itemKey(player.getUseItem());
            boolean firstAccepted = player.hurtServer(level, player.damageSources().generic(), 5f);
            float afterFirst = player.getHealth();
            int afterFirstInvulnerability = player.invulnerableTime;
            boolean secondAccepted = player.hurtServer(level, player.damageSources().generic(), 3f);
            float afterSecond = player.getHealth();
            int afterSecondInvulnerability = player.invulnerableTime;
            boolean thirdAccepted = player.hurtServer(level, player.damageSources().generic(), 8f);
            float afterThird = player.getHealth();
            int afterThirdInvulnerability = player.invulnerableTime;
            return new CooldownTrace(
                abilityInvulnerable,
                beforeInvulnerability,
                armor,
                toughness,
                absorption,
                activeEffects,
                usingItem,
                useItem,
                firstAccepted,
                afterFirst,
                afterFirstInvulnerability,
                secondAccepted,
                afterSecond,
                afterSecondInvulnerability,
                thirdAccepted,
                afterThird,
                afterThirdInvulnerability
            );
        });

        if (Math.abs(12f - trace.afterThird()) > EPSILON) {
            throw new AssertionError("hurt_cooldown_delta expected=12 actual=" + trace.afterThird() + " trace=" + trace);
        }
    }

    private static void validateDeathProtection(
        TestSingleplayerContext singleplayer,
        InteractionHand hand
    ) {
        PopState state = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = onlyPlayer(server);
            reset(player, 4f);
            player.setItemInHand(hand, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.hurtServer((ServerLevel) player.level(), player.damageSources().generic(), 20f);
            return new PopState(player.getHealth(), player.getItemInHand(hand).isEmpty());
        });

        assertClose("death_protection_" + hand.name().toLowerCase(), 1f, state.health(), EPSILON);
        if (!state.consumed()) {
            throw new AssertionError("death protection item was not consumed from " + hand);
        }
    }

    private static void reset(ServerPlayer player, float health) {
        player.removeAllEffects();
        player.setAbsorptionAmount(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.invulnerableTime = 0;
        player.setHealth(health);
    }

    private static ServerPlayer onlyPlayer(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.size() != 1) {
            throw new AssertionError("expected one gametest player, found " + players.size());
        }
        return players.getFirst();
    }

    private static PlayerSnapshot cleanSnapshot(float health) {
        return new PlayerSnapshot(
            health,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
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

    private static String itemKey(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void assertClose(String id, float expected, float actual, float tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(id + " expected=" + expected + " actual=" + actual);
        }
    }

    private record PopState(float health, boolean consumed) {
    }

    private record CooldownTrace(
        boolean abilityInvulnerable,
        int beforeInvulnerability,
        float armor,
        double toughness,
        float absorption,
        int activeEffects,
        boolean usingItem,
        String useItem,
        boolean firstAccepted,
        float afterFirst,
        int afterFirstInvulnerability,
        boolean secondAccepted,
        float afterSecond,
        int afterSecondInvulnerability,
        boolean thirdAccepted,
        float afterThird,
        int afterThirdInvulnerability
    ) {
    }
}
