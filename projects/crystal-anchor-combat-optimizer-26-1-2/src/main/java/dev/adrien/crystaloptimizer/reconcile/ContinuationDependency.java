package dev.adrien.crystaloptimizer.reconcile;

import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Observable fact that must be consumed before a deferred continuation advances. */
public sealed interface ContinuationDependency permits ContinuationDependency.CrystalGone {
    boolean satisfiedBy(CombatEvent event);

    boolean expired(long nowNanos);

    record CrystalGone(
        int entityId,
        BlockPos basePos,
        long expiresAtNanos
    ) implements ContinuationDependency {
        public CrystalGone(int entityId, BlockPos basePos) {
            this(entityId, basePos, Long.MAX_VALUE);
        }

        public CrystalGone {
            if (entityId <= 0) {
                throw new IllegalArgumentException("entityId must be positive");
            }
            Objects.requireNonNull(basePos, "basePos");
            basePos = basePos.immutable();
            if (expiresAtNanos < 0L) {
                throw new IllegalArgumentException("expiresAtNanos must be non-negative");
            }
        }

        @Override
        public boolean satisfiedBy(CombatEvent event) {
            return event instanceof CombatEvent.CrystalRemoved removed
                && removed.entityId() == entityId
                && removed.basePos().equals(basePos);
        }

        @Override
        public boolean expired(long nowNanos) {
            if (nowNanos < 0L) {
                throw new IllegalArgumentException("nowNanos must be non-negative");
            }
            return nowNanos > expiresAtNanos;
        }
    }
}
