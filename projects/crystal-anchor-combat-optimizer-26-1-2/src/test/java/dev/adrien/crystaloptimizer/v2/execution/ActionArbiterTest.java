package dev.adrien.crystaloptimizer.v2.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.state.SpawnCrystalCycle;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class ActionArbiterTest {
    private final ActionArbiter arbiter = new ActionArbiter();
    private final UUID targetId = UUID.randomUUID();
    private final BlockPos base = new BlockPos(4, 64, 7);
    private final int crystalId = 712;

    @Test
    void breakThenSameBasePlaceUsesPredictedPostBreakLegality() {
        ActionApproval approval = approval(4.0f);
        FakeView view = matchingView();
        view.counts.put(Items.END_CRYSTAL, 1);

        ArbitrationResult result = arbiter.evaluate(
            approval,
            List.of(new AttackKnownCrystal(crystalId), new PlaceCrystal(base)),
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertTrue(result.allowed());
        assertEquals(ArbitrationResult.Reason.NONE, result.reason());
    }

    @Test
    void rejectsStaleRemovedOutOfReachUnsafeAndReservedActions() {
        ActionApproval approval = approval(4.0f);
        FakeView view = matchingView();
        view.counts.put(Items.END_CRYSTAL, 1);

        view.worldRevision = 99L;
        assertReason(approval, view, new PendingItemLedger(), ArbitrationResult.Reason.STALE_APPROVAL);
        view.worldRevision = 3L;

        view.liveCrystal = false;
        assertReason(approval, view, new PendingItemLedger(), ArbitrationResult.Reason.ENTITY_GONE);
        view.liveCrystal = true;

        view.entityReach = false;
        assertReason(approval, view, new PendingItemLedger(), ArbitrationResult.Reason.ENTITY_OUT_OF_REACH);
        view.entityReach = true;

        ActionApproval unsafe = approval(13.0f);
        assertReason(unsafe, view, new PendingItemLedger(), ArbitrationResult.Reason.SELF_DAMAGE_LIMIT);

        PendingItemLedger reserved = new PendingItemLedger();
        reserved.reserve(900L, Items.END_CRYSTAL, 1, 1);
        assertReason(approval, view, reserved, ArbitrationResult.Reason.ITEM_UNAVAILABLE);
    }

    @Test
    void healthDropAfterScanRejectsPreviouslyComfortableExplosionAsSelfLethal() {
        FakeView view = matchingView();
        view.selfEffectiveHealth = 8.0f;
        view.counts.put(Items.END_CRYSTAL, 1);
        ActionApproval approval = new ActionApproval(
            88L,
            targetId,
            ApprovalSlot.PLACE,
            new FixedActionSequence(List.of(new PlaceCrystal(base))),
            DamageEstimate.exact(16.0f, 3L, 5L),
            OpportunityIntent.PRESSURE,
            new SelfDamageEstimate(10.0f, 10.0f, false),
            ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0),
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            5_000L
        );

        ArbitrationResult result = arbiter.evaluate(
            approval,
            List.of(new PlaceCrystal(base)),
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertFalse(result.allowed());
        assertEquals(ArbitrationResult.Reason.SELF_LETHAL, result.reason());
    }

    @Test
    void modeledLocalTotemPopIsRejectedEvenForLethalSpeedKill() {
        FakeView view = matchingView();
        view.selfEffectiveHealth = 20.0f;
        ActionApproval approval = new ActionApproval(
            89L,
            targetId,
            ApprovalSlot.LETHAL,
            new FixedActionSequence(List.of(new AttackKnownCrystal(crystalId))),
            DamageEstimate.exact(40.0f, 3L, 5L),
            OpportunityIntent.LETHAL,
            new SelfDamageEstimate(19.0f, 1.0f, true),
            ResourceChain.none(),
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            5_000L
        );

        ArbitrationResult result = arbiter.evaluate(
            approval,
            List.of(new AttackKnownCrystal(crystalId)),
            view,
            new PendingItemLedger(),
            OptimizerConfig.defaults(),
            500L
        );

        assertFalse(result.allowed());
        assertEquals(ArbitrationResult.Reason.SELF_TOTEM_POP, result.reason());
    }

    @Test
    void continuationCanUseItemsAlreadyReservedByItsOwnCompleteChain() {
        FakeView view = matchingView();
        view.counts.put(Items.END_CRYSTAL, 1);
        ResourceChain resources = ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0);
        List<CombatAction> actions = List.of(new SelectHotbarSlot(0), new PlaceCrystal(base));
        ActionApproval approval = new ActionApproval(
            90L,
            targetId,
            ApprovalSlot.PREPARE,
            new FixedActionSequence(actions),
            DamageEstimate.exact(12.0f, 3L, 5L),
            OpportunityIntent.PRESSURE,
            new SelfDamageEstimate(2.0f, 18.0f, false),
            resources,
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            5_000L
        );
        PendingItemLedger ledger = new PendingItemLedger();
        ledger.reserveChain(12345L, resources, view::observedCount);

        ArbitrationResult result = arbiter.evaluateFrom(
            approval,
            actions,
            1,
            view,
            ledger,
            OptimizerConfig.defaults(),
            500L
        );

        assertTrue(result.allowed(), "continuation must not count its own group reservation against itself");
    }

    private void assertReason(
        ActionApproval approval,
        FakeView view,
        PendingItemLedger ledger,
        ArbitrationResult.Reason expected
    ) {
        ArbitrationResult result = arbiter.evaluate(
            approval,
            List.of(new AttackKnownCrystal(crystalId), new PlaceCrystal(base)),
            view,
            ledger,
            OptimizerConfig.defaults(),
            500L
        );
        assertFalse(result.allowed());
        assertEquals(expected, result.reason());
    }

    private ActionApproval approval(float selfDamage) {
        return new ActionApproval(
            77L,
            targetId,
            ApprovalSlot.RECYCLE,
            new SpawnCrystalCycle(base, true),
            DamageEstimate.exact(18.0f, 3L, 5L),
            selfDamage,
            SequenceTiming.immediate(),
            3L,
            9L,
            11L,
            13L,
            5_000L
        );
    }

    private FakeView matchingView() {
        FakeView view = new FakeView();
        view.worldRevision = 3L;
        view.targetRevision = 9L;
        view.inventoryRevision = 11L;
        view.configRevision = 13L;
        return view;
    }

    private final class FakeView implements LiveCombatView {
        long worldRevision;
        long targetRevision;
        long inventoryRevision;
        long configRevision;
        boolean targetValid = true;
        boolean liveCrystal = true;
        boolean entityReach = true;
        boolean blockReach = true;
        boolean followBreak = true;
        float selfEffectiveHealth = 20.0f;
        boolean selfTotemAvailable;
        final Map<Item, Integer> counts = new HashMap<>();

        @Override public long worldRevision() { return worldRevision; }
        @Override public long targetRevision(UUID id) { return targetRevision; }
        @Override public long inventoryRevision() { return inventoryRevision; }
        @Override public long configRevision() { return configRevision; }
        @Override public boolean targetValid(UUID id) { return targetValid && targetId.equals(id); }
        @Override public boolean liveCrystal(int id) { return liveCrystal && id == crystalId; }
        @Override public boolean withinEntityReach(int id) { return entityReach && id == crystalId; }
        @Override public boolean withinBlockReach(BlockPos pos) { return blockReach && base.equals(pos); }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos pos, int id) {
            return followBreak && base.equals(pos) && id == crystalId;
        }
        @Override public int observedCount(Item item) { return counts.getOrDefault(item, 0); }
        @Override public int selectedHotbarSlot() { return 0; }
        @Override public float selfEffectiveHealth() { return selfEffectiveHealth; }
        @Override public boolean selfTotemAvailable() { return selfTotemAvailable; }
    }
}
