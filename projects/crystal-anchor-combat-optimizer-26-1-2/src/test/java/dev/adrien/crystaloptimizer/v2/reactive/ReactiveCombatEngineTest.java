package dev.adrien.crystaloptimizer.v2.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.state.SpawnCrystalCycle;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ReactiveCombatEngineTest {
    private final UUID target = UUID.randomUUID();
    private final BlockPos base = new BlockPos(4, 64, 7);

    @Test
    void realSpawnIdBecomesRecycleAttackAndDuplicateEventIsSuppressed() {
        ReactiveCombatEngine engine = new ReactiveCombatEngine();
        ActionApproval recycle = approval(
            10L,
            ApprovalSlot.RECYCLE,
            new SpawnCrystalCycle(base, true)
        );
        CombatBlackboardSnapshot snapshot = snapshot(Map.of(ApprovalSlot.RECYCLE, recycle));
        CombatEvent.CrystalSpawned event = new CombatEvent.CrystalSpawned(712, base, 1_000L);

        ReactiveDecision decision = engine.decide(event, snapshot, 1_050L).orElseThrow();
        assertEquals(
            java.util.List.of(new AttackKnownCrystal(712), new PlaceCrystal(base)),
            decision.actions()
        );
        assertTrue(engine.decide(event, snapshot, 1_060L).isEmpty());
    }

    @Test
    void popFinisherPreemptsRecycle() {
        ReactiveCombatEngine engine = new ReactiveCombatEngine();
        ActionApproval finisher = approval(
            20L,
            ApprovalSlot.FINISHER,
            new FixedActionSequence(java.util.List.of(new DetonateAnchor(base)))
        );
        ActionApproval recycle = approval(
            21L,
            ApprovalSlot.RECYCLE,
            new SpawnCrystalCycle(base, true)
        );
        CombatBlackboardSnapshot snapshot = snapshot(Map.of(
            ApprovalSlot.FINISHER, finisher,
            ApprovalSlot.RECYCLE, recycle
        ));

        ReactiveDecision decision = engine.decide(
            new CombatEvent.TotemPopped(target, 2_000L),
            snapshot,
            2_010L
        ).orElseThrow();

        assertEquals(ApprovalSlot.FINISHER, decision.slot());
        assertEquals(java.util.List.of(new DetonateAnchor(base)), decision.actions());
    }

    private ActionApproval approval(
        long id,
        ApprovalSlot slot,
        dev.adrien.crystaloptimizer.v2.state.ReactiveActionSpec spec
    ) {
        return new ActionApproval(
            id,
            target,
            slot,
            spec,
            DamageEstimate.exact(18.0f, 3L, 5L),
            4.0f,
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            10_000L
        );
    }

    private CombatBlackboardSnapshot snapshot(Map<ApprovalSlot, ActionApproval> approvals) {
        return new CombatBlackboardSnapshot(target, 9L, 3L, 11L, 13L, approvals);
    }
}
