package dev.adrien.crystaloptimizer.reconcile;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public sealed interface PlanAssumption permits
    PlanAssumption.BlockStateAssumption,
    PlanAssumption.CrystalExistsAssumption,
    PlanAssumption.InventorySlotAssumption,
    PlanAssumption.TargetWithinAssumption,
    PlanAssumption.MinimumTimingConfidenceAssumption,
    PlanAssumption.DimensionAssumption,
    PlanAssumption.SimulatedScalarAssumption {

    boolean relevant(ReconciliationEvent event);

    Optional<ReconciliationFailure> failure(ReconciliationEvent event);

    default boolean clearAllPredictionsOnFailure() {
        return false;
    }

    static PlanAssumption blockState(BlockPos pos, String blockId) {
        return new BlockStateAssumption(pos, blockId);
    }

    static PlanAssumption crystalExists(int entityId) {
        return new CrystalExistsAssumption(entityId);
    }

    static PlanAssumption inventorySlot(int slot, String itemId, int minimumCount) {
        return new InventorySlotAssumption(slot, itemId, minimumCount);
    }

    static PlanAssumption targetWithin(UUID targetId, Vec3 center, double radius) {
        return new TargetWithinAssumption(targetId, center, radius);
    }

    static PlanAssumption minimumTimingConfidence(double minimumConfidence) {
        return new MinimumTimingConfidenceAssumption(minimumConfidence);
    }

    static PlanAssumption dimension(String dimensionKey) {
        return new DimensionAssumption(dimensionKey);
    }

    static PlanAssumption simulatedScalar(String key, double expected, double tolerance) {
        return new SimulatedScalarAssumption(key, expected, tolerance);
    }

    record BlockStateAssumption(BlockPos pos, String blockId) implements PlanAssumption {
        public BlockStateAssumption {
            Objects.requireNonNull(pos, "pos");
            if (blockId == null || blockId.isBlank()) {
                throw new IllegalArgumentException("blockId must not be blank");
            }
            pos = pos.immutable();
        }

        @Override
        public boolean relevant(ReconciliationEvent event) {
            return event instanceof ReconciliationEvent.BlockStateEvent block && block.pos().equals(pos);
        }

        @Override
        public Optional<ReconciliationFailure> failure(ReconciliationEvent event) {
            if (!(event instanceof ReconciliationEvent.BlockStateEvent block) || !block.pos().equals(pos)) {
                return Optional.empty();
            }
            if (block.blockId().equals(blockId)) {
                return Optional.empty();
            }
            return Optional.of(new ReconciliationFailure(
                FailureKind.STATE_RACE,
                this,
                event,
                "required block changed from " + blockId + " to " + block.blockId()
            ));
        }
    }

    record CrystalExistsAssumption(int entityId) implements PlanAssumption {
        public CrystalExistsAssumption {
            if (entityId < 0) {
                throw new IllegalArgumentException("entityId must be non-negative");
            }
        }

        @Override
        public boolean relevant(ReconciliationEvent event) {
            return event instanceof ReconciliationEvent.CrystalPresenceEvent crystal
                && crystal.entityId() == entityId;
        }

        @Override
        public Optional<ReconciliationFailure> failure(ReconciliationEvent event) {
            if (!(event instanceof ReconciliationEvent.CrystalPresenceEvent crystal)
                || crystal.entityId() != entityId
                || crystal.present()) {
                return Optional.empty();
            }
            return Optional.of(new ReconciliationFailure(
                FailureKind.STATE_RACE,
                this,
                event,
                "required crystal " + entityId + " is no longer present"
            ));
        }
    }

    record InventorySlotAssumption(int slot, String itemId, int minimumCount) implements PlanAssumption {
        public InventorySlotAssumption {
            if (slot < 0) {
                throw new IllegalArgumentException("slot must be non-negative");
            }
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
            if (minimumCount < 0) {
                throw new IllegalArgumentException("minimumCount must be non-negative");
            }
        }

        @Override
        public boolean relevant(ReconciliationEvent event) {
            return event instanceof ReconciliationEvent.InventorySlotEvent inventory
                && inventory.slot() == slot;
        }

        @Override
        public Optional<ReconciliationFailure> failure(ReconciliationEvent event) {
            if (!(event instanceof ReconciliationEvent.InventorySlotEvent inventory) || inventory.slot() != slot) {
                return Optional.empty();
            }
            if (inventory.itemId().equals(itemId) && inventory.count() >= minimumCount) {
                return Optional.empty();
            }
            return Optional.of(new ReconciliationFailure(
                FailureKind.RESOURCE_FAILURE,
                this,
                event,
                "required slot " + slot + " no longer has at least " + minimumCount + " of " + itemId
            ));
        }
    }

    record TargetWithinAssumption(UUID targetId, Vec3 center, double radius) implements PlanAssumption {
        public TargetWithinAssumption {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(center, "center");
            requireFinite(center, "center");
            if (!Double.isFinite(radius) || radius < 0.0) {
                throw new IllegalArgumentException("radius must be non-negative and finite");
            }
        }

        @Override
        public boolean relevant(ReconciliationEvent event) {
            return event instanceof ReconciliationEvent.TargetPositionEvent target
                && target.targetId().equals(targetId);
        }

        @Override
        public Optional<ReconciliationFailure> failure(ReconciliationEvent event) {
            if (!(event instanceof ReconciliationEvent.TargetPositionEvent target)
                || !target.targetId().equals(targetId)) {
                return Optional.empty();
            }
            if (target.position().distanceToSqr(center) <= radius * radius) {
                return Optional.empty();
            }
            return Optional.of(new ReconciliationFailure(
                FailureKind.TARGET_DIVERGENCE,
                this,
                event,
                "target left the committed robust geometry region"
            ));
        }
    }

    record MinimumTimingConfidenceAssumption(double minimumConfidence) implements PlanAssumption {
        public MinimumTimingConfidenceAssumption {
            if (!Double.isFinite(minimumConfidence) || minimumConfidence < 0.0 || minimumConfidence > 1.0) {
                throw new IllegalArgumentException("minimumConfidence must be in [0, 1]");
            }
        }

        @Override
        public boolean relevant(ReconciliationEvent event) {
            return event instanceof ReconciliationEvent.TimingConfidenceEvent;
        }

        @Override
        public Optional<ReconciliationFailure> failure(ReconciliationEvent event) {
            if (!(event instanceof ReconciliationEvent.TimingConfidenceEvent timing)
                || timing.confidence() >= minimumConfidence) {
                return Optional.empty();
            }
            return Optional.of(new ReconciliationFailure(
                FailureKind.NETWORK_UNCERTAINTY,
                this,
                event,
                "timing confidence dropped below committed threshold"
            ));
        }
    }

    record DimensionAssumption(String dimensionKey) implements PlanAssumption {
        public DimensionAssumption {
            if (dimensionKey == null || dimensionKey.isBlank()) {
                throw new IllegalArgumentException("dimensionKey must not be blank");
            }
        }

        @Override
        public boolean relevant(ReconciliationEvent event) {
            return event instanceof ReconciliationEvent.DimensionEvent;
        }

        @Override
        public Optional<ReconciliationFailure> failure(ReconciliationEvent event) {
            if (!(event instanceof ReconciliationEvent.DimensionEvent dimension)
                || dimension.dimensionKey().equals(dimensionKey)) {
                return Optional.empty();
            }
            return Optional.of(new ReconciliationFailure(
                FailureKind.LEGALITY_FAILURE,
                this,
                event,
                "dimension changed from " + dimensionKey + " to " + dimension.dimensionKey()
            ));
        }

        @Override
        public boolean clearAllPredictionsOnFailure() {
            return true;
        }
    }

    record SimulatedScalarAssumption(String key, double expected, double tolerance) implements PlanAssumption {
        public SimulatedScalarAssumption {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
            if (!Double.isFinite(expected)) {
                throw new IllegalArgumentException("expected must be finite");
            }
            if (!Double.isFinite(tolerance) || tolerance < 0.0) {
                throw new IllegalArgumentException("tolerance must be non-negative and finite");
            }
        }

        @Override
        public boolean relevant(ReconciliationEvent event) {
            return event instanceof ReconciliationEvent.SimulatedScalarEvent scalar
                && scalar.key().equals(key);
        }

        @Override
        public Optional<ReconciliationFailure> failure(ReconciliationEvent event) {
            if (!(event instanceof ReconciliationEvent.SimulatedScalarEvent scalar)
                || !scalar.key().equals(key)
                || Math.abs(scalar.actual() - expected) <= tolerance) {
                return Optional.empty();
            }
            return Optional.of(new ReconciliationFailure(
                FailureKind.SIMULATION_MISMATCH,
                this,
                event,
                "simulated " + key + " expected " + expected + " but observed " + scalar.actual()
            ));
        }
    }

    private static void requireFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
