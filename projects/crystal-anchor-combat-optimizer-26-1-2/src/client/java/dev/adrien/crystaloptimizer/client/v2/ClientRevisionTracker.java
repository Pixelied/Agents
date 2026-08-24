package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientRevisionTracker {
    private static final ClientRevisionTracker INSTANCE = new ClientRevisionTracker();

    private final AtomicLong worldRevision = new AtomicLong();
    private final AtomicLong inventoryRevision = new AtomicLong();
    private final ConcurrentHashMap<UUID, AtomicLong> targetRevisions = new ConcurrentHashMap<>();

    public ClientRevisionTracker() {
    }

    public static ClientRevisionTracker instance() {
        return INSTANCE;
    }

    public long worldRevision() {
        return worldRevision.get();
    }

    public long inventoryRevision() {
        return inventoryRevision.get();
    }

    public long targetRevision(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        AtomicLong revision = targetRevisions.get(targetId);
        return revision == null ? 0L : revision.get();
    }

    public long markInventoryMutation() {
        return inventoryRevision.incrementAndGet();
    }

    public long markTargetMovement(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        return targetRevisions
            .computeIfAbsent(targetId, ignored -> new AtomicLong())
            .incrementAndGet();
    }

    public void observe(CombatEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof CombatEvent.BlockChanged) {
            worldRevision.incrementAndGet();
            return;
        }
        if (event instanceof CombatEvent.InventoryChanged inventory) {
            inventoryRevision.accumulateAndGet(inventory.inventoryRevision(), Math::max);
            return;
        }
        if (event instanceof CombatEvent.TargetMoved moved) {
            targetRevisions
                .computeIfAbsent(moved.targetId(), ignored -> new AtomicLong())
                .accumulateAndGet(moved.targetRevision(), Math::max);
            return;
        }
        if (event instanceof CombatEvent.EquipmentChanged equipment) {
            targetRevisions
                .computeIfAbsent(equipment.targetId(), ignored -> new AtomicLong())
                .incrementAndGet();
        }
    }
}
