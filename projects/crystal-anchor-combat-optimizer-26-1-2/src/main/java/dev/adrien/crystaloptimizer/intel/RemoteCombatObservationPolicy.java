package dev.adrien.crystaloptimizer.intel;

import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import java.util.Objects;

public final class RemoteCombatObservationPolicy {
    public static HurtWindowState hurtWindow(int invulnerableTime) {
        if (invulnerableTime < 0) {
            throw new IllegalArgumentException("invulnerableTime must be non-negative");
        }
        return invulnerableTime == 0
            ? new HurtWindowState(0, 0.0f)
            : HurtWindowState.unknownThreshold(invulnerableTime);
    }

    public static float absorptionUpperBound(EffectState effects) {
        Objects.requireNonNull(effects, "effects");
        return effects.absorption()
            .map(effect -> 4.0f * Math.max(0, effect.amplifier() + 1))
            .orElse(0.0f);
    }

    public static TotemState visibleTotem(boolean mainHandTotem, boolean offHandTotem) {
        if (mainHandTotem && offHandTotem) {
            return TotemState.BOTH;
        }
        if (mainHandTotem) {
            return TotemState.MAINHAND;
        }
        if (offHandTotem) {
            return TotemState.OFFHAND;
        }
        return TotemState.NONE;
    }

    private RemoteCombatObservationPolicy() {
    }
}
