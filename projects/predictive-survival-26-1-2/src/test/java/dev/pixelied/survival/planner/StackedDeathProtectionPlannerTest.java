package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.config.RescueProfile;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackedDeathProtectionPlannerTest {
    @Test
    void prearmsSecondHandWhenExistingTotemWillBeConsumedBeforeAnotherLethalHit() {
        DeathProtectionSnapshot.ProtectionItem totem = DeathProtectionSnapshot.ProtectionItem.vanillaTotem();
        PredictionContext context = context(DeathProtectionSnapshot.offHand(totem));
        ThreatTimeline timeline = stackedLethalTimeline();
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 1, false),
                1, new InventorySlotSnapshot(1, "minecraft:totem_of_undying", 1, true),
                40, new InventorySlotSnapshot(40, "minecraft:totem_of_undying", 1, true)
            ),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36, 1, 37, 40, 45));

        List<SurvivalAction> candidates = new SurvivalCandidateGenerator().generate(
            context, timeline, inventory, menu, RescuePolicy.smartDefaults()
        );

        SurvivalAction.EquipDeathProtection prearm = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            candidates.stream().filter(SurvivalAction.EquipDeathProtection.class::isInstance).findFirst().orElseThrow()
        );
        assertEquals(SurvivalAction.Hand.MAIN_HAND, prearm.hand());

        SurvivalPlan plan = new SurvivalPlanner().plan(context, timeline, candidates, SafetyMode.SAFE);
        SurvivalAction.EquipDeathProtection chosen = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class, plan.action()
        );
        assertEquals(SurvivalAction.Hand.MAIN_HAND, chosen.hand());
        assertTrue(plan.simulation().result().survived());
        assertEquals(2, plan.simulation().result().consumedDeathProtectionCount());
    }

    @Test
    void prearmsBothHandsFromInventoryWhenNoTotemIsHeldAndTwoPopsAreRequired() {
        PredictionContext context = context(DeathProtectionSnapshot.none());
        ThreatTimeline timeline = spacedStackedLethalTimeline();
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 1, false),
                1, new InventorySlotSnapshot(1, "minecraft:totem_of_undying", 1, true),
                2, new InventorySlotSnapshot(2, "minecraft:totem_of_undying", 1, true),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, false)
            ),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36, 1, 37, 2, 38, 40, 45));

        List<SurvivalAction> candidates = new SurvivalCandidateGenerator().generate(
            context, timeline, inventory, menu, RescuePolicy.smartDefaults()
        );

        List<SurvivalAction.EquipDeathProtection> protection = candidates.stream()
            .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
            .map(SurvivalAction.EquipDeathProtection.class::cast)
            .toList();
        assertEquals(2, protection.size(), "stacked lethal window should expose one equip action per hand");
        assertTrue(protection.stream().anyMatch(action -> action.hand() == SurvivalAction.Hand.MAIN_HAND));
        assertTrue(protection.stream().anyMatch(action -> action.hand() == SurvivalAction.Hand.OFF_HAND));

        ContingencyPlan plan = new ContingencyPlanner().plan(
            context, timeline, candidates, SafetyMode.SAFE, RescueProfile.SMART
        );
        assertTrue(plan.guaranteed());
        assertEquals(2, plan.steps().size());
        assertTrue(plan.steps().stream().allMatch(step -> step.action() instanceof SurvivalAction.EquipDeathProtection));
        assertEquals(2, plan.result().consumedDeathProtectionCount());
    }

    @Test
    void doesNotPretendOneStackCanBeSplitAcrossBothHands() {
        PredictionContext context = context(DeathProtectionSnapshot.none());
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 1, false),
                1, new InventorySlotSnapshot(1, "test:stackable_protection", 2, true),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, false)
            ),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 4, Map.of(0, 36, 1, 37, 40, 45));

        List<SurvivalAction.EquipDeathProtection> protection = new SurvivalCandidateGenerator().generate(
                context, spacedStackedLethalTimeline(), inventory, menu, RescuePolicy.smartDefaults()
            ).stream()
            .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
            .map(SurvivalAction.EquipDeathProtection.class::cast)
            .toList();

        assertEquals(1, protection.size(),
            "whole-stack routing cannot arm both hands from one source slot without a split-stack executor");
    }

    private static PredictionContext context(DeathProtectionSnapshot protection) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(), protection,
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of("max_health", "20")
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline stackedLethalTimeline() {
        return stackedLethalTimeline(2, 3);
    }

    private static ThreatTimeline spacedStackedLethalTimeline() {
        return stackedLethalTimeline(4, 8);
    }

    private static ThreatTimeline stackedLethalTimeline(long firstTick, long secondTick) {
        DamageSourceSnapshot arrow = new DamageSourceSnapshot(
            DamageRange.exact(100f), Set.of(), false, 1f, false, Optional.empty(), "test:arrow"
        );
        DamageSourceSnapshot mace = new DamageSourceSnapshot(
            DamageRange.exact(220f), Set.of(), false, 1f, false, Optional.empty(), "test:mace"
        );
        return new ThreatTimeline(List.of(
            new ThreatEvent(
                "first", ThreatKind.PROJECTILE, new TickWindow(firstTick, firstTick), arrow, Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            ),
            new ThreatEvent(
                "second", ThreatKind.MELEE, new TickWindow(secondTick, secondTick), mace, Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            )
        ));
    }
}
