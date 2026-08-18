package dev.adrien.crystaloptimizer.reconcile;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public sealed interface ReconciliationEvent permits
    ReconciliationEvent.BlockStateEvent,
    ReconciliationEvent.CrystalPresenceEvent,
    ReconciliationEvent.InventorySlotEvent,
    ReconciliationEvent.TargetPositionEvent,
    ReconciliationEvent.TimingConfidenceEvent,
    ReconciliationEvent.DimensionEvent,
    ReconciliationEvent.SimulatedScalarEvent {

    long timestampNanos();

    static ReconciliationEvent blockState(BlockPos pos, String blockId, long timestampNanos) {
        return new BlockStateEvent(pos, blockId, timestampNanos);
    }

    static ReconciliationEvent crystalPresence(int entityId, boolean present, long timestampNanos) {
        return new CrystalPresenceEvent(entityId, present, timestampNanos);
    }

    static ReconciliationEvent inventorySlot(int slot, String itemId, int count, long timestampNanos) {
        return new InventorySlotEvent(slot, itemId, count, timestampNanos);
    }

    static ReconciliationEvent targetPosition(UUID targetId, Vec3 position, long timestampNanos) {
        return new TargetPositionEvent(targetId, position, timestampNanos);
    }

    static ReconciliationEvent timingConfidence(double confidence, long timestampNanos) {
        return new TimingConfidenceEvent(confidence, timestampNanos);
    }

    static ReconciliationEvent dimension(String dimensionKey, long timestampNanos) {
        return new DimensionEvent(dimensionKey, timestampNanos);
    }

    static ReconciliationEvent simulatedScalar(String key, double actual, long timestampNanos) {
        return new SimulatedScalarEvent(key, actual, timestampNanos);
    }

    static void requireTimestamp(long timestampNanos) {
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must be non-negative");
        }
    }

    record BlockStateEvent(BlockPos pos, String blockId, long timestampNanos) implements ReconciliationEvent {
        public BlockStateEvent {
            Objects.requireNonNull(pos, "pos");
            if (blockId == null || blockId.isBlank()) {
                throw new IllegalArgumentException("blockId must not be blank");
            }
            pos = pos.immutable();
            requireTimestamp(timestampNanos);
        }
    }

    record CrystalPresenceEvent(int entityId, boolean present, long timestampNanos) implements ReconciliationEvent {
        public CrystalPresenceEvent {
            if (entityId < 0) {
                throw new IllegalArgumentException("entityId must be non-negative");
            }
            requireTimestamp(timestampNanos);
        }
    }

    record InventorySlotEvent(int slot, String itemId, int count, long timestampNanos) implements ReconciliationEvent {
        public InventorySlotEvent {
            if (slot < 0) {
                throw new IllegalArgumentException("slot must be non-negative");
            }
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
            if (count < 0) {
                throw new IllegalArgumentException("count must be non-negative");
            }
            requireTimestamp(timestampNanos);
        }
    }

    record TargetPositionEvent(UUID targetId, Vec3 position, long timestampNanos) implements ReconciliationEvent {
        public TargetPositionEvent {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(position, "position");
            requireFinite(position, "position");
            requireTimestamp(timestampNanos);
        }
    }

    record TimingConfidenceEvent(double confidence, long timestampNanos) implements ReconciliationEvent {
        public TimingConfidenceEvent {
            if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be in [0, 1]");
            }
            requireTimestamp(timestampNanos);
        }
    }

    record DimensionEvent(String dimensionKey, long timestampNanos) implements ReconciliationEvent {
        public DimensionEvent {
            if (dimensionKey == null || dimensionKey.isBlank()) {
                throw new IllegalArgumentException("dimensionKey must not be blank");
            }
            requireTimestamp(timestampNanos);
        }
    }

    record SimulatedScalarEvent(String key, double actual, long timestampNanos) implements ReconciliationEvent {
        public SimulatedScalarEvent {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
            if (!Double.isFinite(actual)) {
                throw new IllegalArgumentException("actual must be finite");
            }
            requireTimestamp(timestampNanos);
        }
    }

    private static void requireFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
