package dev.adrien.crystaloptimizer.v2.execution;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.reconcile.ContinuationDependency;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3RecycleContinuationTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000813");
    private static final BlockPos BASE = new BlockPos(4, 64, 7);
    private static final int CRYSTAL_ID = 712;

    @Test
    void consumedCrystalGoneDependencyAllowsDeferredSameBasePlaceAfterServerRemoval() {
        List<CombatAction> actions = List.of(
            new AttackKnownCrystal(CRYSTAL_ID),
            new PlaceCrystal(BASE)
        );
        ActionApproval approval = new ActionApproval(
            77L,
            TARGET,
            ApprovalSlot.RECYCLE,
            new FixedActionSequence(actions),
            DamageEstimate.exact(18.0f, 3L, 5L),
            OpportunityIntent.PRESSURE,
            new SelfDamageEstimate(2.0f, 18.0f, false),
            ResourceChain.none(),
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            5_000L
        );
        ContinuationDependency.CrystalGone gone = new ContinuationDependency.CrystalGone(
            CRYSTAL_ID,
            BASE,
            2_000L
        );
        FakeView view = new FakeView();

        ArbitrationResult result = new ActionArbiter().evaluateContinuation(
            approval,
            actions,
            1,
            123L,
            Set.of(gone),
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertTrue(result.allowed());
        assertEquals(0, view.followBreakChecks,
            "consumed CrystalGone must never be rechecked against the removed entity");
    }

    @Test
    void crystalGoneDependencyMatchesOnlyTheConfirmedServerRemoval() {
        ContinuationDependency.CrystalGone gone = new ContinuationDependency.CrystalGone(
            CRYSTAL_ID,
            BASE
        );

        assertTrue(gone.satisfiedBy(new dev.adrien.crystaloptimizer.v2.reactive.CombatEvent.CrystalRemoved(
            CRYSTAL_ID,
            BASE,
            1_000L
        )));
        assertTrue(!gone.satisfiedBy(new dev.adrien.crystaloptimizer.v2.reactive.CombatEvent.CrystalRemoved(
            CRYSTAL_ID + 1,
            BASE,
            1_000L
        )));
    }

    private static final class FakeView implements LiveCombatView {
        int followBreakChecks;

        @Override public long worldRevision() { return 3L; }
        @Override public long targetRevision(UUID id) { return 9L; }
        @Override public long inventoryRevision() { return 11L; }
        @Override public long configRevision() { return 13L; }
        @Override public boolean targetValid(UUID id) { return TARGET.equals(id); }
        @Override public boolean liveCrystal(int id) { return false; }
        @Override public boolean withinEntityReach(int id) { return false; }
        @Override public boolean withinBlockReach(BlockPos pos) { return BASE.equals(pos); }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos pos, int id) {
            followBreakChecks++;
            return false;
        }
        @Override public int observedCount(Item item) { return item == Items.END_CRYSTAL ? 1 : 0; }
        @Override public int selectedHotbarSlot() { return 0; }
        @Override public float selfEffectiveHealth() { return 20.0f; }
        @Override public boolean selfTotemAvailable() { return false; }
    }
}
