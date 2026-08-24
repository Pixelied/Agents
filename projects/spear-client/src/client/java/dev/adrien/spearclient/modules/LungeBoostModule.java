package dev.adrien.spearclient.modules;

import dev.adrien.spearclient.combat.AttackSequence;
import dev.adrien.spearclient.combat.SpearContext;
import dev.adrien.spearclient.network.MovementPath;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.Vec3;

public final class LungeBoostModule {
    private static final double CONSERVATIVE_FORWARD_DISTANCE = 8.5;
    private static final AtomicLong SEQUENCE_IDS = new AtomicLong(2_000_000L);

    private final boolean enabled;

    public LungeBoostModule(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Optional<AttackSequence> afterStab(SpearContext context) {
        if (!enabled
            || context == null
            || context.piercing() == null
            || context.lungeLevel() <= 0
            || !context.vanillaLungeEligible()) {
            return Optional.empty();
        }

        Vec3 direction = context.look().normalize();
        Vec3 forward = context.origin().add(direction.scale(CONSERVATIVE_FORWARD_DISTANCE));
        MovementPath path = MovementPath.of(context.origin(), List.of(forward));
        return Optional.of(new AttackSequence(
            SEQUENCE_IDS.incrementAndGet(),
            AttackSequence.Kind.LUNGE,
            context,
            path,
            false,
            -1,
            CONSERVATIVE_FORWARD_DISTANCE,
            1,
            20
        ));
    }
}
