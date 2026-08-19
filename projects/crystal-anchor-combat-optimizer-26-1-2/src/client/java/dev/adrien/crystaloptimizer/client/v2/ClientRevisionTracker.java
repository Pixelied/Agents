package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class ClientRevisionTracker {
    private final AtomicLong worldRevision = new AtomicLong();
    private final AtomicLong inventoryRevision = new AtomicLong();
    private final ConcurrentHashMap<UUID, AtomicLong> targetRevisions = new ConcurrentHashMap<>();

    long worldRevision() {
        return worldRevision.get();
    }

    long inventoryRevision() {
        return inventoryRevision.get();
    }

    long targetRevision(UUID targetId) {
        AtomicLong revision = targetRevisions.get(targetId);
        return revision == null ? 0L : revision.get();
    }

    void observe(CombatEvent event) {
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
