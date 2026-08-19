package dev.adrien.crystaloptimizer.v2.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class CombatBlackboardTest {
    @Test
    void spawnCycleUsesOnlyObservedIdAndBlackboardSnapshotIsImmutable() {
        UUID target = UUID.randomUUID();
        BlockPos base = new BlockPos(4, 63, 7);
        ActionApproval approval = new ActionApproval(
            77L,
            target,
            ApprovalSlot.RECYCLE,
            new SpawnCrystalCycle(base, true),
            DamageEstimate.exact(18.0f, 3L, 5L),
            6.0f,
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            5_000L
        );
        CombatBlackboard board = new CombatBlackboard();
        board.publish(new CombatBlackboardSnapshot(
            target,
            9L,
            3L,
            11L,
            13L,
            Map.of(ApprovalSlot.RECYCLE, approval)
        ));

        List<CombatAction> actions = approval.actionSpec().materialize(
            new CombatEvent.CrystalSpawned(412, base, 1_000L)
        );
        assertEquals(List.of(new AttackKnownCrystal(412), new PlaceCrystal(base)), actions);
        assertThrows(UnsupportedOperationException.class,
            () -> board.snapshot().approvals().clear());
    }
}
