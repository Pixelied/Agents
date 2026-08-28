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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CausalProtectionPlanningTest {
    private final SurvivalCandidateGenerator generator = new SurvivalCandidateGenerator();

    @Test
    void mutuallyDestructiveCrystalsDoNotRequestRedundantSecondTotem() {
        ThreatEvent crystalA = explosion("explosion:201", 10f);
        ThreatEvent crystalB = explosion("explosion:202", 20f);
        ThreatTimeline flat = new ThreatTimeline(List.of(crystalA, crystalB));
        CausalThreatTimeline causal = new CausalThreatTimeline(
            flat,
            Map.of(
                crystalA.id(), "entity:201",
                crystalB.id(), "entity:202"
            ),
            Map.of(
                crystalA.id(), List.of(new ThreatTransition.RemoveSource("entity:202")),
                crystalB.id(), List.of(new ThreatTransition.RemoveSource("entity:201"))
            )
        );

        List<SurvivalAction> flatCandidates = generator.generate(
            context(), flat, inventory(), menu(), RescuePolicy.totemOnly()
        );
        assertEquals(1, protectionCandidates(flatCandidates),
            "flat planning must demonstrate the stale second-Totem overcommitment");

        List<SurvivalAction> causalCandidates = generator.generate(
            context(), causal, inventory(), menu(), RescuePolicy.totemOnly()
        );

        assertEquals(0, protectionCandidates(causalCandidates),
            "one already-held Totem is sufficient when either crystal removes the other source");
    }

    @Test
    void independentAnchorsStillRequestSecondTotem() {
        ThreatEvent anchorA = explosion("anchor:301", 10f);
        ThreatEvent anchorB = explosion("anchor:302", 20f);
        CausalThreatTimeline causal = new CausalThreatTimeline(
            new ThreatTimeline(List.of(anchorA, anchorB)),
            Map.of(
                anchorA.id(), "block:anchor:10,64,10",
                anchorB.id(), "block:anchor:11,64,10"
            ),
            Map.of()
        );

        List<SurvivalAction> candidates = generator.generate(
            context(), causal, inventory(), menu(), RescuePolicy.totemOnly()
        );

        assertEquals(1, protectionCandidates(candidates),
            "a sibling charged anchor remains a separate lethal source and still requires replenishment");
        SurvivalAction.EquipDeathProtection action = (SurvivalAction.EquipDeathProtection) candidates.stream()
            .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
            .findFirst()
            .orElseThrow();
        assertEquals(SurvivalAction.Hand.OFF_HAND, action.hand());
        assertTrue(action.sourceItem().isPresent());
    }

    @Test
    void immediatePotentialCrystalFallbackStillUsesCausalRemoval() {
        ThreatEvent crystalA = potentialExplosion("explosion:201", 20f);
        ThreatEvent crystalB = potentialExplosion("explosion:202", 20f);
        PredictionContext context = immediateCrystalContext();
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.MAIN_HAND,
            0,
            true,
            true,
            1d,
            1,
            2
        );

        SurvivalPlan plan = new SurvivalPlanner().plan(
            context,
            new ThreatTimeline(List.of(crystalA, crystalB)),
            List.of(protection),
            SafetyMode.BALANCED
        );

        assertTrue(plan.action() instanceof SurvivalAction.EquipDeathProtection,
            "a tick-zero potential crystal must still trigger one best-effort Totem when its sibling source is removed");
        assertEquals(DeadlineStatus.BEST_EFFORT, plan.simulation().deadlineStatus());
    }

    private static long protectionCandidates(List<SurvivalAction> candidates) {
        return candidates.stream().filter(SurvivalAction.EquipDeathProtection.class::isInstance).count();
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

    private static PredictionContext immediateCrystalContext() {
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
            new AabbSnapshot(-0.3, 0, -0.3, 0.3, 1.8, 0.3),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(
                List.of(crystalSnapshot("201", 2d), crystalSnapshot("202", 5d)),
                List.of()
            ),
            new TimingSnapshot(0, 100, 0, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot crystalSnapshot(String id, double z) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            "minecraft:end_crystal",
            new Vec3Snapshot(0, 0, z),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(-1, 0, z - 1, 1, 2, z + 1),
            Map.of(
                "explosion_radius", "6.0",
                "triggerable", "true",
                "source_key", "minecraft:explosion",
                "scales_with_difficulty", "true"
            )
        );
    }

    private static InventorySnapshot inventory() {
        return new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:totem_of_undying", 1, true),
                5, new InventorySlotSnapshot(5, "minecraft:totem_of_undying", 1, true),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, false)
            ),
            false
        );
    }

    private static MenuSlotMap menu() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 5, 41, 40, 45));
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

    private static ThreatEvent potentialExplosion(String id, float rawDamage) {
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
            new TickWindow(0, 2),
            damage,
            Confidence.POTENTIAL,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
    }
}
