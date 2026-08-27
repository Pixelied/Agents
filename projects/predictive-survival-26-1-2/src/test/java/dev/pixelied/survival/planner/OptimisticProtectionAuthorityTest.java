package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
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
import dev.pixelied.survival.execution.EquipmentAuthorityProjection;
import dev.pixelied.survival.execution.PendingEquipmentMutation;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OptimisticProtectionAuthorityTest {
    @Test
    void optimisticLocalTotemCannotSuppressEquipBeforeServerAuthority() {
        InventorySlotSnapshot sword = slot(0, "minecraft:diamond_sword", false);
        InventorySlotSnapshot totem = slot(5, "minecraft:totem_of_undying", true);
        InventorySlotSnapshot offhand = slot(40, "minecraft:air", false);

        // The local client has already selected slot 5, so its LocalPlayer snapshot renders a Totem
        // in main hand. The server-authority projection still has slot 0 confirmed and the select
        // packet may not be processed until tick 4.
        InventorySnapshot locallyOptimisticInventory = new InventorySnapshot(
            5,
            Map.of(0, sword, 5, totem, 40, offhand),
            false
        );
        EquipmentAuthorityProjection equipment = new EquipmentAuthorityProjection(
            0,
            sword,
            offhand,
            List.of(new PendingEquipmentMutation(
                SurvivalAction.Hand.MAIN_HAND,
                sword,
                totem,
                new TickWindow(2, 4),
                PendingEquipmentMutation.Origin.EMERGENCY_PROTECTION,
                1L
            )),
            1L
        );

        List<SurvivalAction> candidates = new AuthorityAwareCandidateGenerator().generate(
            optimisticContext(),
            lethalTimeline(3),
            locallyOptimisticInventory,
            new MenuSlotMap(0, 4, Map.of(0, 36, 5, 41, 40, 45)),
            RescuePolicy.smartDefaults(),
            equipment
        );

        SurvivalAction.EquipDeathProtection equip = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            candidates.stream()
                .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "optimistic LocalPlayer protection must not suppress a server-required equip"
                ))
        );
        assertEquals(SurvivalAction.Hand.MAIN_HAND, equip.hand());
        assertEquals(new DeathProtectionRoute.HotbarSelect(5), equip.sourceItem().orElseThrow().route());
    }

    private static PredictionContext optimisticContext() {
        DeathProtectionSnapshot optimisticProtection = new DeathProtectionSnapshot(
            Optional.of(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            Optional.empty()
        );
        PlayerSnapshot player = new PlayerSnapshot(
            5f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            optimisticProtection,
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)),
            EngineLimits.defaults()
        );
    }

    private static ThreatTimeline lethalTimeline(long impactTick) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:optimistic-authority"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "optimistic-authority",
            ThreatKind.OTHER,
            new TickWindow(impactTick, impactTick),
            damage,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        )));
    }

    private static InventorySlotSnapshot slot(int index, String key, boolean protection) {
        return new InventorySlotSnapshot(index, key, key.hashCode(), 1, protection,
            Optional.empty(), Optional.empty(), Optional.empty(),
            protection ? Optional.of(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()) : Optional.empty(),
            false);
    }
}
