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
    OpportunityIntent intent,
    SelfDamageEstimate selfDamage,
    ResourceChain resources,
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
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(selfDamage, "selfDamage");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(geometryDependencies, "geometryDependencies");
        if (id.isBlank()) {
            throw new IllegalArgumentException("opportunity id must not be blank");
        }
        geometryDependencies = Set.copyOf(geometryDependencies);
    }

    public DamageOpportunity(
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
        this(
            id,
            actionSpec,
            targetDamage,
            legacyIntent(lethal, popsTotem),
            SelfDamageEstimate.legacy(worstCaseSelfDamage),
            ResourceChain.none(),
            timing,
            lethal,
            popsTotem,
            positionDependent,
            geometryDependencies
        );
    }

    public float worstCaseSelfDamage() {
        return selfDamage.worstCaseDamage();
    }

    private static OpportunityIntent legacyIntent(boolean lethal, boolean popsTotem) {
        if (lethal) {
            return OpportunityIntent.LETHAL;
        }
        if (popsTotem) {
            return OpportunityIntent.POP;
        }
        return OpportunityIntent.PRESSURE;
    }
}
