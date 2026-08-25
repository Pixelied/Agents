package dev.pixelied.survival.core;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.DeadlineStatus;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.planner.SurvivalCandidateGenerator;
import dev.pixelied.survival.planner.SurvivalPlanner;
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import dev.pixelied.survival.threat.opportunity.OpportunityTimelineAssembler;
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

class SurvivalEngineOpportunityFrameTest {
    @Test
    void opportunityCanMakePlanningLethalWithoutPollutingActualTimeline() {
        ThreatTimeline actual = new ThreatTimeline(List.of());
        LethalOpportunity opportunity = lethalOpportunityAtTick("opportunity:test:burst", 1);
        ThreatTimeline planning = new OpportunityTimelineAssembler().assemble(actual, List.of(opportunity), 128);
        SurvivalEngine.EngineFrame frame = new SurvivalEngine.EngineFrame(
            context(), actual, List.of(opportunity), planning, List.of()
        );

        assertTrue(frame.actualTimeline().events().isEmpty());
        assertEquals(1, frame.opportunities().size());
        assertEquals("opportunity:test:burst", frame.planningTimeline().events().getFirst().id());
    }

    @Test
    void crystalOpportunityPreArmsOneTickHotbarTotemBeforeCrystalExists() {
        PredictionContext context = context();
        ThreatTimeline actual = new ThreatTimeline(List.of());
        LethalOpportunity opportunity = crystalOpportunityAtTick("opportunity:crystal:test", 0);
        ThreatTimeline planning = new OpportunityTimelineAssembler().assemble(actual, List.of(opportunity), 128);
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 1, false),
                1, new InventorySlotSnapshot(1, "minecraft:totem_of_undying", 1, true)
            ),
            false
        );
        MenuSlotMap menu = new MenuSlotMap(0, 0, Map.of());

        List<SurvivalAction> candidates = new SurvivalCandidateGenerator().generate(
            context, planning, inventory, menu, RescuePolicy.totemOnly()
        );
        SurvivalAction.EquipDeathProtection generated = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            candidates.stream()
                .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                .findFirst()
                .orElseThrow()
        );
        var plan = new SurvivalPlanner().plan(context, planning, candidates, SafetyMode.BALANCED);
        SurvivalAction.EquipDeathProtection chosen = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            plan.action()
        );

        assertTrue(actual.events().isEmpty(), "no EndCrystal threat exists in the actual timeline yet");
        assertEquals(1, generated.requiredServerTicks(), "hotbar protection route must stay one server tick");
        assertEquals(generated.hand(), chosen.hand());
        assertEquals(DeadlineStatus.BEST_EFFORT, plan.simulation().deadlineStatus());
        assertTrue(plan.simulation().result().survived());
    }

    private static LethalOpportunity lethalOpportunityAtTick(String id, long tick) {
        return opportunityAtTick(id, tick, OpportunityFamily.OTHER, 1);
    }

    private static LethalOpportunity crystalOpportunityAtTick(String id, long tick) {
        return opportunityAtTick(id, tick, OpportunityFamily.CRYSTAL, 2);
    }

    private static LethalOpportunity opportunityAtTick(
        String id,
        long tick,
        OpportunityFamily family,
        int actionDepth
    ) {
        ThreatEvent event = new ThreatEvent(
            id,
            ThreatKind.OTHER,
            new TickWindow(tick, tick),
            new DamageSourceSnapshot(
                DamageRange.exact(100f), Set.of(), false, 1f, false, Optional.empty(), "test:burst"
            ),
            Confidence.POTENTIAL,
            Optional.empty(), Optional.empty(), false, false, false, false
        );
        return new LethalOpportunity(id, family, event, Confidence.POTENTIAL, actionDepth, Map.of());
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)), EngineLimits.defaults()
        );
    }
}
