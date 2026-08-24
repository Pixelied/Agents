package dev.adrien.crystaloptimizer.v2.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContinuationInventoryRevisionTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000e01");
    private static final BlockPos BASE = new BlockPos(4, 64, 7);
    private static final long APPROVAL_INVENTORY_REVISION = 11L;
    private static final long POST_BURST_INVENTORY_REVISION = 12L;

    private final ActionArbiter arbiter = new ActionArbiter();

    @Test
    void continuationAcceptsExactPostBurstInventoryRevision() {
        List<CombatAction> actions = List.of(
            new SelectHotbarSlot(1),
            new PlaceCrystal(BASE)
        );
        FakeView view = view(POST_BURST_INVENTORY_REVISION);
        view.counts.put(Items.END_CRYSTAL, 1);

        ArbitrationResult result = arbiter.evaluateFromContinuation(
            approval(actions, ResourceChain.none()),
            actions,
            1,
            POST_BURST_INVENTORY_REVISION,
            Set.of(),
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertTrue(result.allowed(),
            "bot-owned slot mutation captured after the burst must not stale its own continuation");
    }

    @Test
    void laterInventoryMutationStillRejectsContinuation() {
        List<CombatAction> actions = List.of(
            new SelectHotbarSlot(1),
            new PlaceCrystal(BASE)
        );
        FakeView view = view(POST_BURST_INVENTORY_REVISION + 1L);
        view.counts.put(Items.END_CRYSTAL, 1);

        ArbitrationResult result = arbiter.evaluateFromContinuation(
            approval(actions, ResourceChain.none()),
            actions,
            1,
            POST_BURST_INVENTORY_REVISION,
            Set.of(),
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertFalse(result.allowed());
        assertEquals(ArbitrationResult.Reason.STALE_APPROVAL, result.reason(),
            "a user/server mutation after the captured bot revision must still fail closed");
    }

    @Test
    void reservedContinuationAlsoAcceptsItsCapturedPostBurstRevision() {
        List<CombatAction> actions = List.of(
            new SelectHotbarSlot(1),
            new PlaceCrystal(BASE)
        );
        ResourceChain resources = ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0);
        ActionApproval approval = approval(actions, resources);
        FakeView view = view(POST_BURST_INVENTORY_REVISION);
        view.counts.put(Items.END_CRYSTAL, 1);
        PendingItemLedger ledger = new PendingItemLedger();
        long reservationId = 9981L;
        ledger.reserveChain(reservationId, resources, view::observedCount);

        ArbitrationResult result = arbiter.evaluateContinuation(
            approval,
            actions,
            1,
            reservationId,
            POST_BURST_INVENTORY_REVISION,
            Set.of(),
            view,
            ledger,
            OptimizerConfig.defaults(),
            500L
        );

        assertTrue(result.allowed());
    }

    private static ActionApproval approval(List<CombatAction> actions, ResourceChain resources) {
        return new ActionApproval(
            91L,
            TARGET,
            ApprovalSlot.PREPARE,
            new FixedActionSequence(actions),
            DamageEstimate.exact(12.0f, 3L, 5L),
            OpportunityIntent.PRESSURE,
            new SelfDamageEstimate(2.0f, 18.0f, false),
            resources,
            SequenceTiming.immediate(),
            3L,
            9L,
            APPROVAL_INVENTORY_REVISION,
            13L,
            5_000L
        );
    }

    private static FakeView view(long inventoryRevision) {
        FakeView view = new FakeView();
        view.inventoryRevision = inventoryRevision;
        return view;
    }

    private static final class FakeView implements LiveCombatView {
        long inventoryRevision;
        final Map<Item, Integer> counts = new HashMap<>();

        @Override public long worldRevision() { return 3L; }
        @Override public long targetRevision(UUID targetId) { return 9L; }
        @Override public long inventoryRevision() { return inventoryRevision; }
        @Override public long configRevision() { return 13L; }
        @Override public boolean targetValid(UUID targetId) { return TARGET.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return true; }
        @Override public boolean withinEntityReach(int entityId) { return true; }
        @Override public boolean withinBlockReach(BlockPos pos) { return BASE.equals(pos); }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) {
            return true;
        }
        @Override public int observedCount(Item item) { return counts.getOrDefault(item, 0); }
        @Override public int selectedHotbarSlot() { return 1; }
        @Override public float selfEffectiveHealth() { return 20.0f; }
        @Override public boolean selfTotemAvailable() { return false; }
    }
}
