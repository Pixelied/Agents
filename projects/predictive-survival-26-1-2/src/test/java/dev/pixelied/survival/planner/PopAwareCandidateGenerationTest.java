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
import dev.pixelied.survival.execution.DeathProtectionPopTracker;
import dev.pixelied.survival.execution.EquipmentAuthorityProjection;
import dev.pixelied.survival.execution.ServerStateEvidenceSnapshot;
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

class PopAwareCandidateGenerationTest {
    @Test
    void unresolvedPopRoutesSeparateReplacementInsteadOfTrustingStaleHeldTotem() {
        InventorySlotSnapshot popped = totem(0, 101);
        InventorySlotSnapshot replacement = totem(2, 202);
        InventorySlotSnapshot offhand = new InventorySlotSnapshot(40, "minecraft:air", 0, false);
        InventorySnapshot inventory = new InventorySnapshot(0, Map.of(
            0, popped,
            2, replacement,
            40, offhand
        ), false);
        EquipmentAuthorityProjection equipment = new EquipmentAuthorityProjection(
            0,
            popped,
            offhand,
            List.of(),
            0L,
            MitigationSnapshot.none()
        );
        DeathProtectionPopTracker pops = new DeathProtectionPopTracker();
        pops.reconcile(equipment, inventory, ServerStateEvidenceSnapshot.unknown(), 0L);
        pops.observeLocalTotemPop(1L, 0L);

        List<SurvivalAction> candidates = new AuthorityAwareCandidateGenerator().generate(
            context(),
            timeline(2L),
            inventory,
            new MenuSlotMap(0, 1, Map.of(0, 36, 2, 38, 40, 45)),
            RescuePolicy.smartDefaults(),
            equipment,
            pops
        );

        SurvivalAction.EquipDeathProtection equip = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            candidates.stream().filter(SurvivalAction.EquipDeathProtection.class::isInstance).findFirst().orElseThrow()
        );
        assertEquals(new DeathProtectionRoute.HotbarSelect(2), equip.sourceItem().orElseThrow().route());
        assertEquals(SurvivalAction.Hand.MAIN_HAND, equip.hand());
    }

    private static PredictionContext context() {
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
            DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
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

    private static ThreatTimeline timeline(long impactTick) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:post-pop-replenishment"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "post-pop-replenishment",
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

    private static InventorySlotSnapshot totem(int index, int fingerprint) {
        return new InventorySlotSnapshot(
            index,
            "minecraft:totem_of_undying",
            fingerprint,
            1,
            true,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            false
        );
    }
}
