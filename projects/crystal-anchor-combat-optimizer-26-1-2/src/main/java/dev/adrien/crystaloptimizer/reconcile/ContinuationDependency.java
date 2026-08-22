package dev.adrien.crystaloptimizer.reconcile;

import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Observable fact that must be consumed before a deferred continuation advances. */
public sealed interface ContinuationDependency permits ContinuationDependency.CrystalGone {
    boolean satisfiedBy(CombatEvent event);

    record CrystalGone(int entityId, BlockPos basePos) implements ContinuationDependency {
        public CrystalGone {
            if (entityId <= 0) {
                throw new IllegalArgumentException("entityId must be positive");
            }
            Objects.requireNonNull(basePos, "basePos");
            basePos = basePos.immutable();
        }

        @Override
        public boolean satisfiedBy(CombatEvent event) {
            return event instanceof CombatEvent.CrystalRemoved removed
                && removed.entityId() == entityId
                && removed.basePos().equals(basePos);
        }
    }
}
