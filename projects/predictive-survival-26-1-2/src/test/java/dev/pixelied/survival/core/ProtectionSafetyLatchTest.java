package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.threat.opportunity.ProtectionContinuity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionSafetyLatchTest {
    @Test
    void activeLatchRejectsShieldRouteThatWouldReplaceOnlyTotemHand() {
        PlayerSnapshot player = player(DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()));
        SurvivalAction.RaiseShield replaceMain = shieldFromHotbar(SurvivalAction.Hand.MAIN_HAND);

        assertFalse(ProtectionContinuity.preservesAuthoritativeProtection(player, replaceMain));
    }

    @Test
    void offhandShieldRouteIsAllowedWhileMainHandTotemRemainsAuthoritative() {
        PlayerSnapshot player = player(DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()));
        SurvivalAction.RaiseShield replaceOffhand = shieldFromContainer(SurvivalAction.Hand.OFF_HAND);

        assertTrue(ProtectionContinuity.preservesAuthoritativeProtection(player, replaceOffhand));
    }

    @Test
    void equippingSecondDeathProtectionIsAllowed() {
        PlayerSnapshot player = player(DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()));
        SurvivalAction.EquipDeathProtection addOffhand = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            1,
            true,
            true,
            1d,
            1,
            1
        );

        assertTrue(ProtectionContinuity.preservesAuthoritativeProtection(player, addOffhand));
    }

    @Test
    void noActionPreservesExistingProtection() {
        PlayerSnapshot player = player(DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()));

        assertTrue(ProtectionContinuity.preservesAuthoritativeProtection(player, new SurvivalAction.NoAction()));
    }

    @Test
    void noCurrentProtectionDoesNotInventAContinuityRequirement() {
        PlayerSnapshot player = player(DeathProtectionSnapshot.none());

        assertTrue(ProtectionContinuity.preservesAuthoritativeProtection(player, shieldFromHotbar(SurvivalAction.Hand.MAIN_HAND)));
    }

    private static SurvivalAction.RaiseShield shieldFromHotbar(SurvivalAction.Hand hand) {
        SurvivalItemRoute route = new SurvivalItemRoute.HotbarSelect(1, hand, "minecraft:shield", 17);
        return shield(hand, route);
    }

    private static SurvivalAction.RaiseShield shieldFromContainer(SurvivalAction.Hand hand) {
        SurvivalItemRoute route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 40, 40, hand, "minecraft:shield", 17, 1
        );
        return shield(hand, route);
    }

    private static SurvivalAction.RaiseShield shield(SurvivalAction.Hand hand, SurvivalItemRoute route) {
        SurvivalAction.HeldItemRef source = new SurvivalAction.HeldItemRef(
            hand,
            "minecraft:shield",
            17,
            Optional.of(route)
        );
        return new SurvivalAction.RaiseShield(
            1,
            true,
            true,
            true,
            1d,
            1f,
            0,
            0,
            1,
            Optional.empty(),
            Optional.of(source)
        );
    }

    private static PlayerSnapshot player(DeathProtectionSnapshot protection) {
        return new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            protection,
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            Map.of("main_hand", "minecraft:totem_of_undying", "off_hand", "minecraft:air")
        );
    }
}
