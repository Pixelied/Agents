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
import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ReactiveBurstDispatcher implements ReactiveBurstSink {
    private static final int GROUP_RESERVATION_INDEX = 255;

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
        return dispatchFrom(decision, config, 0);
    }

    @Override
    public BurstReceipt dispatchFrom(
        ReactiveDecision decision,
        OptimizerConfig config,
        int startIndex
    ) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(config, "config");
        if (startIndex < 0 || startIndex > decision.actions().size()) {
            throw new IllegalArgumentException("startIndex outside reactive decision");
        }
        if (startIndex == decision.actions().size()) {
            return BurstReceipt.empty();
        }

        List<DispatchReceipt> receipts = new ArrayList<>();
        List<Long> reservationIds = new ArrayList<>();
        boolean critical = decision.slot() != ApprovalSlot.PREPARE;
        ResourceChain resourceChain = decision.approval().resources();
        long groupReservationId = reservationId(decision.actionId(), GROUP_RESERVATION_INDEX);
        boolean groupedReservation = !resourceChain.isEmpty();

        if (groupedReservation) {
            if (startIndex == 0) {
                try {
                    pendingItems.reserveChain(
                        groupReservationId,
                        resourceChain,
                        view::observedCount
                    );
                    reservationIds.add(groupReservationId);
                } catch (IllegalStateException unavailable) {
                    return new BurstReceipt(
                        List.of(DispatchReceipt.failed("resource chain unavailable")),
                        List.of()
                    );
                }
            } else if (!pendingItems.hasReservation(groupReservationId)) {
                return new BurstReceipt(
                    List.of(DispatchReceipt.failed("resource chain reservation missing")),
                    List.of()
                );
            }
        }

        boolean sentAny = false;
        for (int index = startIndex; index < decision.actions().size(); index++) {
            CombatAction action = decision.actions().get(index);
            long perActionReservationId = groupedReservation
                ? -1L
                : reserveIfNeeded(decision.actionId(), index, action);
            if (perActionReservationId >= 0L) {
                reservationIds.add(perActionReservationId);
            }

            DispatchReceipt receipt = dispatcher.dispatch(
                action,
                config.rotationMode(),
                critical
            );
            receipts.add(receipt);
            if (receipt.status() == DispatchReceipt.Status.SENT) {
                sentAny = true;
                continue;
            }

            if (perActionReservationId >= 0L) {
                pendingItems.release(perActionReservationId);
                reservationIds.remove(perActionReservationId);
            }
            if (groupedReservation && startIndex == 0 && !sentAny) {
                pendingItems.release(groupReservationId);
                reservationIds.remove(groupReservationId);
            }
            break;
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
