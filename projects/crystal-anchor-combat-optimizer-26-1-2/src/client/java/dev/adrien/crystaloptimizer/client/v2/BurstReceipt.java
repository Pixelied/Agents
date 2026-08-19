package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.client.execution.DispatchReceipt;
import java.util.List;
import java.util.Objects;

public record BurstReceipt(
    List<DispatchReceipt> receipts,
    List<Long> pendingReservationIds
) {
    public BurstReceipt {
        Objects.requireNonNull(receipts, "receipts");
        Objects.requireNonNull(pendingReservationIds, "pendingReservationIds");
        receipts = List.copyOf(receipts);
        pendingReservationIds = List.copyOf(pendingReservationIds);
    }

    public static BurstReceipt empty() {
        return new BurstReceipt(List.of(), List.of());
    }

    public boolean complete() {
        return !receipts.isEmpty()
            && receipts.stream().allMatch(receipt -> receipt.status() == DispatchReceipt.Status.SENT);
    }
}
