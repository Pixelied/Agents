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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
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
            waitForServerClientLoaded(context, singleplayer);

            results.add(validateGenericDamage(singleplayer));
            results.addAll(DamageValidationScenarios.firstRuntimeSlice(context, singleplayer));
            results.addAll(ExplosionValidationScenarios.runtimeSlice(singleplayer));
            results.addAll(ProjectileValidationScenarios.runtimeSlice(context, singleplayer));
            ProjectileSnapshotRangeValidationScenarios.validateClientTrackedDistantArrowIsSnapshotted(context, singleplayer);
            ProjectileSnapshotCapacityValidationScenarios.validateHarmlessTrackedEntitiesCannotCrowdOutDamagingProjectile(context, singleplayer);
            ProjectileSnapshotCapacityValidationScenarios.validateRelevantProjectileOverflowFailsClosed(context, singleplayer);
            EvokerFangsSnapshotCapacityValidationScenarios.validateFangsCannotBeSilentlyDroppedByRelevantEntityBudget(context, singleplayer);
            ShulkerBulletValidationScenarios.validateVisibleBulletProducesPreImpactThreat(context, singleplayer);
            GuardianBeamValidationScenarios.validateActiveBeamProducesPreImpactSequence(context, singleplayer);
            WardenSonicBoomValidationScenarios.validateObservedChargeProducesSonicThreat(context, singleplayer);
            EvokerFangsValidationScenarios.validateVisibleFangsProducePreImpactThreat(context, singleplayer);
            ContactHazardRuntimeValidationScenarios.validateMagmaContactReachesProductionRuntime(context, singleplayer);
            ReactiveThornsRuntimeValidationScenarios.validateVisibleTwoPieceThornsReachesProductionRuntime(context, singleplayer);
            TippedArrowPotionValidationScenarios.validateTippedArrowWitherHasPreImpactThreat(context, singleplayer);
            DragonFireballValidationScenarios.validateObservableDamageHasPreImpactThreat(context, singleplayer);
            PotionValidationScenarios.validateSplashHarmingHasPreImpactThreat(context, singleplayer);
            WallSplashHarmingValidationScenarios.validateWallFalloffMatchesVanilla(context, singleplayer);
            WallSplashPoisonValidationScenarios.validateWallSplashPoisonHasThreat(context, singleplayer);
            PotionValidationScenarios.validateLingeringHarmingHasPreImpactThreat(context, singleplayer);
            LingeringPotionCloudHandoffValidationScenarios.validateLiveCloudRetainsHarmingThreat(context, singleplayer);
            PoisonPotionValidationScenarios.validateSplashPoisonHasPreImpactThreat(context, singleplayer);
            PoisonPersistenceValidationScenarios.validateActivePoisonRetainsFutureThreat(context, singleplayer);
            WitherPotionValidationScenarios.validateSplashWitherHasPreImpactThreat(context, singleplayer);
            StackedWitherPotionValidationScenarios.validateHiddenWitherTailIsPredictedBeforeImpact(context, singleplayer);
            PostImpactStackedStatusPersistenceValidationScenarios.validateHiddenWitherTailSurvivesProjectileRemoval(context, singleplayer);
            WitherPersistenceValidationScenarios.validateActiveWitherRetainsFutureThreat(context, singleplayer);
            InfiniteWitherValidationScenarios.validateInfiniteWitherUsesBoundedPhase(context, singleplayer);
            LingeringWitherCloudValidationScenarios.validateLingeringWitherRetainsThreatAcrossCloudHandoff(context, singleplayer);
            LingeringPoisonCloudValidationScenarios.validateLingeringPoisonRetainsThreatAcrossCloudHandoff(context, singleplayer);
            PotionSnapshotMetadataValidationScenarios.validateObservablePotionTimingMetadata(context, singleplayer);
            results.addAll(ExtendedValidationScenarios.runtimeSlice(context, singleplayer));
            results.addAll(FallRescueValidationScenarios.runtimeSlice(context, singleplayer));
            results.addAll(ExperimentalValidationScenarios.runtimeSlice(singleplayer));
            InventoryValidationScenarios.validateOffhandSwapAndLethalPop(context, singleplayer);
            NonTotemCandidateValidationScenarios.validateLiveItemCapabilities(context, singleplayer);
            results.addAll(LiveExplosionScalingValidationScenarios.runtimeSlice(context, singleplayer));
            MinecartTntValidationScenarios.validatePrimedMinecartProducesBoundedExplosionThreat(context, singleplayer);
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

    private static void waitForServerClientLoaded(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean loaded = singleplayer.getServer().computeOnServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                return players.size() == 1 && players.getFirst().connection.hasClientLoaded();
            });
            if (loaded) return;
            context.waitTick();
        }
        throw new AssertionError("server player did not report client-loaded readiness before timeout");
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
        assertClose("generic_damage", predicted, actual, EPSILON);
        return new ValidationResult("generic_damage", predicted, actual, ValidationStatus.RUNTIME_CONFIRMED, EPSILON);
    }

    private static void validateHurtCooldown(TestSingleplayerContext singleplayer) {
        float actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = onlyPlayer(server);
            reset(player, 20f);
            ServerLevel level = (ServerLevel) player.level();
            player.hurtServer(level, player.damageSources().generic(), 8f);
            player.hurtServer(level, player.damageSources().generic(), 12f);
            return player.getHealth();
        });

        assertClose("hurt_cooldown_sequence", 8f, actual, EPSILON);
    }

    private static void validateDeathProtection(TestSingleplayerContext singleplayer, InteractionHand hand) {
        PopState state = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = onlyPlayer(server);
            reset(player, 20f);
            if (hand == InteractionHand.MAIN_HAND) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            } else {
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            player.hurtServer((ServerLevel) player.level(), player.damageSources().generic(), 100f);
            return new PopState(player.getHealth(), player.getItemInHand(hand).isEmpty());
        });

        assertClose("death_protection_" + hand.name().toLowerCase(), 1f, state.health(), EPSILON);
        if (!state.consumed()) {
            throw new AssertionError("death protection item was not consumed from " + hand);
        }
    }

    static void reset(ServerPlayer player, float health) {
        player.stopUsingItem();
        player.removeAllEffects();
        player.setAbsorptionAmount(0f);
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.invulnerableTime = 0;
        player.setHealth(health);
    }

    static ServerPlayer onlyPlayer(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.size() != 1) {
            throw new AssertionError("expected one gametest player, found " + players.size());
        }
        return players.getFirst();
    }

    private static PlayerSnapshot cleanSnapshot(float health) {
        return new PlayerSnapshot(
            health, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }

    static void assertClose(String id, float expected, float actual, float tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(id + " expected=" + expected + " actual=" + actual);
        }
    }

    private record PopState(float health, boolean consumed) {
    }
}
