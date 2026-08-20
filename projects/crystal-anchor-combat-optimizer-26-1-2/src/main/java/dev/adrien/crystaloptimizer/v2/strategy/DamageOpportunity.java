package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.ReactiveActionSpec;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

public record DamageOpportunity(
    String id,
    ReactiveActionSpec actionSpec,
    DamageEstimate targetDamage,
    float worstCaseSelfDamage,
    SequenceTiming timing,
    boolean lethal,
    boolean popsTotem,
    boolean positionDependent,
    Set<BlockPos> geometryDependencies
) {
    public DamageOpportunity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actionSpec, "actionSpec");
        Objects.requireNonNull(targetDamage, "targetDamage");
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(geometryDependencies, "geometryDependencies");
        if (id.isBlank()) {
            throw new IllegalArgumentException("opportunity id must not be blank");
        }
        if (!Float.isFinite(worstCaseSelfDamage) || worstCaseSelfDamage < 0.0f) {
            throw new IllegalArgumentException("self damage must be finite and non-negative");
        }
        geometryDependencies = Set.copyOf(geometryDependencies);
    }
}
