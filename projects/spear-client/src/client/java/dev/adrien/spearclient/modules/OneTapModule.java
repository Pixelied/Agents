package dev.adrien.spearclient.modules;

import dev.adrien.spearclient.combat.AttackSequence;
import dev.adrien.spearclient.combat.KineticDamageModel;
import dev.adrien.spearclient.combat.SpearContext;
import dev.adrien.spearclient.network.MovementPath;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class OneTapModule {
    private static final double CONSERVATIVE_TARGET_RAW_DAMAGE = 72.0;
    private static final double BASE_PLAYER_ATTACK_DAMAGE = 1.0;
    private static final double MIN_BACK_DISTANCE = 6.0;
    private static final double MAX_BACK_DISTANCE = 8.5;
    private static final AtomicLong SEQUENCE_IDS = new AtomicLong();

    private final boolean enabled;

    public OneTapModule(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Optional<AttackSequence> prepare(SpearContext context) {
        if (!enabled || context == null || context.piercing() == null || context.kinetic() == null) {
            return Optional.empty();
        }
        if (context.ticksUsingItem() < context.kinetic().delayTicks()) {
            return Optional.empty();
        }

        double damageMultiplier = context.kinetic().damageMultiplier();
        if (!Double.isFinite(damageMultiplier) || damageMultiplier <= 0.0) {
            return Optional.empty();
        }

        double requiredKnownMovement = KineticDamageModel.requiredKnownMovement(
            CONSERVATIVE_TARGET_RAW_DAMAGE,
            BASE_PLAYER_ATTACK_DAMAGE,
            damageMultiplier
        );
        double backDistance = Math.min(
            MAX_BACK_DISTANCE,
            Math.max(MIN_BACK_DISTANCE, requiredKnownMovement + 0.5)
        );
        MovementPath path = MovementPath.conservativeBackReturn(
            context.origin(),
            context.look(),
            backDistance
        );

        return Optional.of(new AttackSequence(
            SEQUENCE_IDS.incrementAndGet(),
            AttackSequence.Kind.ONE_TAP,
            context,
            path,
            false,
            -1,
            backDistance,
            path.positions().size(),
            20
        ));
    }
}
