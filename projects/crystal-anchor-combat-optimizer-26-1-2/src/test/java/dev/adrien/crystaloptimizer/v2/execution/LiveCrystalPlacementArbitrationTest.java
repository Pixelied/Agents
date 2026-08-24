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

final class LiveCrystalPlacementArbitrationTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-00000000c201");
    private static final BlockPos BASE = new BlockPos(4, 64, 7);
    private static final int CRYSTAL_ID = 712;

    private final ActionArbiter arbiter = new ActionArbiter();

    @Test
    void ordinaryPlacementRejectsWhenCurrentBaseIsNoLongerPlaceable() {
        FakeView view = matchingView();
        view.crystalBasePlaceable = false;
        view.counts.put(Items.END_CRYSTAL, 1);
        List<CombatAction> actions = List.of(new PlaceCrystal(BASE));

        ArbitrationResult result = arbiter.evaluate(
            approval(actions),
            actions,
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertFalse(result.allowed());
        assertEquals(ArbitrationResult.Reason.ILLEGAL_TRANSITION, result.reason());
    }

    @Test
    void preRemovalBreakPlaceMayIgnoreOnlyTheCrystalBeingBroken() {
        FakeView view = matchingView();
        view.crystalBasePlaceable = false;
        view.followBreak = true;
        view.counts.put(Items.END_CRYSTAL, 1);
        List<CombatAction> actions = List.of(
            new AttackKnownCrystal(CRYSTAL_ID),
            new PlaceCrystal(BASE)
        );

        ArbitrationResult result = arbiter.evaluate(
            approval(actions),
            actions,
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertTrue(result.allowed(),
            "the known crystal being attacked may be the sole temporary collision before removal");
    }

    @Test
    void postRemovalContinuationRechecksCurrentPlacementLegality() {
        FakeView view = matchingView();
        view.crystalBasePlaceable = false;
        view.followBreak = false;
        view.counts.put(Items.END_CRYSTAL, 1);
        List<CombatAction> actions = List.of(
            new AttackKnownCrystal(CRYSTAL_ID),
            new PlaceCrystal(BASE)
        );
        Set<ContinuationDependency> consumed = Set.of(
            new ContinuationDependency.CrystalGone(CRYSTAL_ID, BASE)
        );

        ArbitrationResult result = arbiter.evaluateFromContinuation(
            approval(actions),
            actions,
            1,
            11L,
            consumed,
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertFalse(result.allowed(),
            "after removal is confirmed, a new blocker or changed base must stop the place");
        assertEquals(ArbitrationResult.Reason.ILLEGAL_TRANSITION, result.reason());
    }

    private static ActionApproval approval(List<CombatAction> actions) {
        return new ActionApproval(
            201L,
            TARGET,
            ApprovalSlot.PLACE,
            new FixedActionSequence(actions),
            DamageEstimate.exact(18.0f, 3L, 5L),
            2.0f,
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            5_000L
        );
    }

    private static FakeView matchingView() {
        FakeView view = new FakeView();
        view.worldRevision = 3L;
        view.targetRevision = 9L;
        view.inventoryRevision = 11L;
        view.configRevision = 13L;
        return view;
    }

    private static final class FakeView implements LiveCombatView {
        long worldRevision;
        long targetRevision;
        long inventoryRevision;
        long configRevision;
        boolean crystalBasePlaceable = true;
        boolean followBreak = true;
        final Map<Item, Integer> counts = new HashMap<>();

        @Override public long worldRevision() { return worldRevision; }
        @Override public long targetRevision(UUID targetId) { return targetRevision; }
        @Override public long inventoryRevision() { return inventoryRevision; }
        @Override public long configRevision() { return configRevision; }
        @Override public boolean targetValid(UUID targetId) { return TARGET.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return entityId == CRYSTAL_ID; }
        @Override public boolean withinEntityReach(int entityId) { return entityId == CRYSTAL_ID; }
        @Override public boolean withinBlockReach(BlockPos pos) { return BASE.equals(pos); }
        @Override public boolean crystalBaseCanPlace(BlockPos basePos) {
            return crystalBasePlaceable && BASE.equals(basePos);
        }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) {
            return followBreak && BASE.equals(basePos) && brokenCrystalEntityId == CRYSTAL_ID;
        }
        @Override public int observedCount(Item item) { return counts.getOrDefault(item, 0); }
        @Override public int selectedHotbarSlot() { return 0; }
        @Override public float selfEffectiveHealth() { return 20.0f; }
    }
}
