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
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.CausalThreatTimeline;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTransition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorityAwareCausalCandidateGenerationTest {
    private final AuthorityAwareCandidateGenerator generator = new AuthorityAwareCandidateGenerator();

    @Test
    void authoritativeMainhandTotemAndCrystalCausalitySuppressRedundantOffhandRoute() {
        CausalThreatTimeline causal = mutuallyDestructiveCrystals();

        List<SurvivalAction> candidates = generator.generate(
            contextWithoutLocalProtection(),
            causal,
            inventory(),
            menu(),
            RescuePolicy.totemOnly(),
            equipment()
        );

        assertEquals(0, protectionCandidates(candidates));
    }

    @Test
    void authoritativeMainhandTotemStillReplenishesForIndependentAnchors() {
        ThreatEvent anchorA = explosion("anchor:601", 10f);
        ThreatEvent anchorB = explosion("anchor:602", 20f);
        CausalThreatTimeline causal = new CausalThreatTimeline(
            new ThreatTimeline(List.of(anchorA, anchorB)),
            Map.of(
                anchorA.id(), "block:anchor:20,64,20",
                anchorB.id(), "block:anchor:21,64,20"
            ),
            Map.of()
        );

        List<SurvivalAction> candidates = generator.generate(
            contextWithoutLocalProtection(),
            causal,
            inventory(),
            menu(),
            RescuePolicy.totemOnly(),
            equipment()
        );

        assertEquals(1, protectionCandidates(candidates));
    }

    @Test
    void unresolvedPopKeepsCausalCrystalPruningWhileRoutingPhysicalReplacements() {
        EquipmentAuthorityProjection equipment = equipment();
        InventorySnapshot inventory = inventoryWithTwoReplacements();
        DeathProtectionPopTracker pops = new DeathProtectionPopTracker();
        pops.reconcile(
            equipment,
            inventory,
            new ServerStateEvidenceSnapshot(true, 1L, Map.of(), Map.of(), Map.of()),
            0L
        );
        pops.observeLocalTotemPop(0L, 1L);

        List<SurvivalAction> candidates = generator.generate(
            contextWithoutLocalProtection(),
            mutuallyDestructiveCrystals(),
            inventory,
            menuWithTwoReplacements(),
            RescuePolicy.totemOnly(),
            equipment,
            pops
        );

        assertEquals(1, protectionCandidates(candidates));
    }

    private static CausalThreatTimeline mutuallyDestructiveCrystals() {
        ThreatEvent crystalA = explosion("explosion:501", 10f);
        ThreatEvent crystalB = explosion("explosion:502", 20f);
        return new CausalThreatTimeline(
            new ThreatTimeline(List.of(crystalA, crystalB)),
            Map.of(
                crystalA.id(), "entity:501",
                crystalB.id(), "entity:502"
            ),
            Map.of(
                crystalA.id(), List.of(new ThreatTransition.RemoveSource("entity:502")),
                crystalB.id(), List.of(new ThreatTransition.RemoveSource("entity:501"))
            )
        );
    }

    private static long protectionCandidates(List<SurvivalAction> candidates) {
        return candidates.stream().filter(SurvivalAction.EquipDeathProtection.class::isInstance).count();
    }

    private static PredictionContext contextWithoutLocalProtection() {
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
            DeathProtectionSnapshot.none(),
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

    private static InventorySnapshot inventory() {
        return new InventorySnapshot(
            0,
            Map.of(
                0, totem(0),
                5, totem(5),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, false)
            ),
            false
        );
    }

    private static InventorySnapshot inventoryWithTwoReplacements() {
        return new InventorySnapshot(
            0,
            Map.of(
                0, totem(0),
                5, totem(5),
                6, totem(6),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, false)
            ),
            false
        );
    }

    private static EquipmentAuthorityProjection equipment() {
        return new EquipmentAuthorityProjection(
            0,
            totem(0),
            new InventorySlotSnapshot(40, "minecraft:air", 0, false),
            List.of(),
            1L
        );
    }

    private static InventorySlotSnapshot totem(int index) {
        return new InventorySlotSnapshot(index, "minecraft:totem_of_undying", 1, true);
    }

    private static MenuSlotMap menu() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 5, 41, 40, 45));
    }

    private static MenuSlotMap menuWithTwoReplacements() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 5, 41, 6, 42, 40, 45));
    }

    private static ThreatEvent explosion(String id, float rawDamage) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:" + id
        );
        return new ThreatEvent(
            id,
            ThreatKind.EXPLOSION,
            new TickWindow(0, 0),
            damage,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
    }
}
