package dev.adrien.spearclient.modules;

import dev.adrien.spearclient.combat.AttackSequence;
import dev.adrien.spearclient.combat.SpearContext;
import dev.adrien.spearclient.network.MovementPath;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class InfiniteReachModule {
    private static final double CONSERVATIVE_STAGE_DISTANCE = 9.0;
    private static final AtomicLong SEQUENCE_IDS = new AtomicLong(1_000_000L);

    private final boolean enabled;

    public InfiniteReachModule(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Optional<AttackSequence> prepare(SpearContext context) {
        if (!enabled || context == null || context.piercing() == null) {
            return Optional.empty();
        }

        MovementPath path = MovementPath.conservativeReach(
            context.origin(),
            context.look(),
            CONSERVATIVE_STAGE_DISTANCE
        );

        return Optional.of(new AttackSequence(
            SEQUENCE_IDS.incrementAndGet(),
            AttackSequence.Kind.REACH,
            context,
            path,
            true,
            1,
            CONSERVATIVE_STAGE_DISTANCE * 2.0,
            path.positions().size(),
            20
        ));
    }
}
