package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.client.execution.ManualOverrideTracker;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.execution.ActionArbiter;
import dev.adrien.crystaloptimizer.v2.execution.ArbitrationResult;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManualOverrideIntegrationTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000914");
    private static final BlockPos BASE = new BlockPos(2, 64, 2);

    @Test
    void manualCombatInputCancelsNewAutomationButPreservesSentLedger() {
        ManualOverrideTracker tracker = ManualOverrideTracker.forTests();
        tracker.setUserControllingCombatInput(true);
        PendingItemLedger ledger = new PendingItemLedger();
        ledger.reserve(55L, Items.END_CRYSTAL, 1, 2);
        PlaceCrystal place = new PlaceCrystal(BASE);
        ActionApproval approval = new ActionApproval(
            91L,
            TARGET,
            ApprovalSlot.PLACE,
            new FixedActionSequence(List.of(place)),
            DamageEstimate.exact(12.0f, 0L, 0L),
            2.0f,
            SequenceTiming.immediate(),
            0L,
            0L,
            0L,
            0L,
            5_000L
        );

        ArbitrationResult result = new ActionArbiter().evaluate(
            approval,
            List.of(place),
            new FakeView(tracker),
            ledger,
            OptimizerConfig.defaults(),
            1_000L
        );

        assertEquals(ArbitrationResult.Reason.MANUAL_OVERRIDE, result.reason());
        assertTrue(ledger.hasReservation(55L),
            "manual override must never erase reconciliation state for already-sent actions");
    }

    private record FakeView(ManualOverrideTracker tracker) implements LiveCombatView {
        @Override public long worldRevision() { return 0L; }
        @Override public long targetRevision(UUID targetId) { return 0L; }
        @Override public long inventoryRevision() { return 0L; }
        @Override public long configRevision() { return 0L; }
        @Override public boolean targetValid(UUID targetId) { return TARGET.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return false; }
        @Override public boolean withinEntityReach(int entityId) { return false; }
        @Override public boolean withinBlockReach(BlockPos pos) { return BASE.equals(pos); }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) { return false; }
        @Override public int observedCount(Item item) { return item == Items.END_CRYSTAL ? 2 : 0; }
        @Override public int selectedHotbarSlot() { return 0; }
        @Override public float selfEffectiveHealth() { return 20.0f; }
        @Override public boolean selfTotemAvailable() { return false; }
        @Override public boolean userControllingCombatInput() { return tracker.isUserControllingCombatInput(); }
    }
}
