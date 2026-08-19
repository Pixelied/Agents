package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.client.execution.DispatchReceipt;
import dev.adrien.crystaloptimizer.client.execution.VanillaInteractionDispatcher;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ReactiveBurstDispatcher implements ReactiveBurstSink {
    private final VanillaInteractionDispatcher dispatcher;
    private final LiveCombatView view;
    private final PendingItemLedger pendingItems;

    public ReactiveBurstDispatcher(
        VanillaInteractionDispatcher dispatcher,
        LiveCombatView view,
        PendingItemLedger pendingItems
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.view = Objects.requireNonNull(view, "view");
        this.pendingItems = Objects.requireNonNull(pendingItems, "pendingItems");
    }

    @Override
    public BurstReceipt dispatch(ReactiveDecision decision, OptimizerConfig config) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(config, "config");
        List<DispatchReceipt> receipts = new ArrayList<>();
        List<Long> reservationIds = new ArrayList<>();
        boolean critical = decision.slot() != ApprovalSlot.PREPARE;

        for (int index = 0; index < decision.actions().size(); index++) {
            CombatAction action = decision.actions().get(index);
            long reservationId = reserveIfNeeded(decision.actionId(), index, action);
            if (reservationId >= 0L) {
                reservationIds.add(reservationId);
            }

            DispatchReceipt receipt = dispatcher.dispatch(
                action,
                config.rotationMode(),
                critical
            );
            receipts.add(receipt);
            if (receipt.status() != DispatchReceipt.Status.SENT) {
                if (reservationId >= 0L) {
                    pendingItems.release(reservationId);
                    reservationIds.remove(reservationId);
                }
                break;
            }
        }

        return new BurstReceipt(receipts, reservationIds);
    }

    public void releaseReservations(BurstReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        for (long reservationId : receipt.pendingReservationIds()) {
            pendingItems.release(reservationId);
        }
    }

    private long reserveIfNeeded(long actionId, int index, CombatAction action) {
        Item item = consumedItem(action);
        if (item == null) {
            return -1L;
        }
        long reservationId = reservationId(actionId, index);
        pendingItems.reserve(
            reservationId,
            item,
            1,
            view.observedCount(item)
        );
        return reservationId;
    }

    private static Item consumedItem(CombatAction action) {
        if (action instanceof PlaceCrystal) {
            return Items.END_CRYSTAL;
        }
        if (action instanceof PlaceObsidian) {
            return Items.OBSIDIAN;
        }
        if (action instanceof PlaceAnchor) {
            return Items.RESPAWN_ANCHOR;
        }
        if (action instanceof ChargeAnchor) {
            return Items.GLOWSTONE;
        }
        return null;
    }

    private static long reservationId(long actionId, int index) {
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException("burst action index outside reservation range");
        }
        if (actionId < 0L || actionId > (Long.MAX_VALUE >>> 8)) {
            throw new IllegalArgumentException("actionId outside reservation range");
        }
        return (actionId << 8) | index;
    }
}
