package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import dev.adrien.crystaloptimizer.planner.PlanScore;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitSchedulerModelTest {
    @Test
    void committedLineDoesNotSwitchForMarginallyBetterPlan() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        CombatPlan planA = plan(0.90, 1.0);
        CombatPlan planB = plan(0.90, 1.1);

        scheduler.arm(planA);
        scheduler.commit();
        scheduler.offer(planB);

        assertSame(planA, scheduler.activePlan());
        assertEquals(CommitPhase.COMMITTED, scheduler.phase());
    }

    @Test
    void emergencyAutoTotemReservationAbortsBeforeUnsentAuraStep() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        scheduler.arm(plan(0.95, 1.0));
        scheduler.commit();

        ReservationResult emergency = inventory.reserve(ReservationRequest.autoTotemEmergency());

        assertTrue(emergency.granted());
        assertEquals(CommitPhase.NORMAL, scheduler.phase());
        assertNull(scheduler.activePlan());
    }

    @Test
    void autoTotemEmergencyPreemptsLowerPriorityAuraOffhandReservation() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        ReservationResult aura = inventory.reserve(ReservationRequest.auraOffhand());

        ReservationResult emergency = inventory.reserve(ReservationRequest.autoTotemEmergency());

        assertTrue(aura.granted());
        assertTrue(emergency.granted());
        assertFalse(inventory.isActive(aura.token().orElseThrow()));
        assertTrue(inventory.isActive(emergency.token().orElseThrow()));
    }

    @Test
    void emergencyDoesNotPretendAlreadySentActionsCanBeUnsent() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        scheduler.arm(singleStepPlan());
        scheduler.commit();
        scheduler.markActionSent();

        inventory.reserve(ReservationRequest.autoTotemEmergency());

        assertEquals(CommitPhase.RECONCILING, scheduler.phase());
    }

    @Test
    void concreteInvalidationAbortsCommittedLineButPlanOffersDoNot() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        CombatPlan committed = plan(0.92, 1.0);
        scheduler.arm(committed);
        scheduler.commit();
        scheduler.offer(plan(0.93, 1.0));

        assertSame(committed, scheduler.activePlan());

        scheduler.abort(CommitAbortReason.MISSING_REQUIRED_EXPLOSIVE);

        assertEquals(CommitPhase.NORMAL, scheduler.phase());
    }

    private static CombatPlan singleStepPlan() {
        List<CombatAction> actions = List.of(new Wait(1));
        return new CombatPlan(
            actions,
            new PlanScore(false, 0.9, 0.8, 1, 0.9, 0.9, 0, 0.05, 1.0, 1.0),
            PacketDependencyGraph.fromActions(actions),
            true,
            0.9
        );
    }

    private static CombatPlan plan(double deathProbability, double futureGeometry) {
        List<CombatAction> actions = List.of(new Wait(1), new Wait(1));
        return new CombatPlan(
            actions,
            new PlanScore(
                false,
                deathProbability,
                0.8,
                2,
                0.9,
                0.9,
                0,
                0.05,
                futureGeometry,
                2.0
            ),
            PacketDependencyGraph.fromActions(actions),
            true,
            0.9
        );
    }
}
