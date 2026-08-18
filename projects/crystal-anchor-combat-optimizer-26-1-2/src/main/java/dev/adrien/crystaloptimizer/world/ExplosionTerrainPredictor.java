package dev.adrien.crystaloptimizer.world;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import java.util.List;
import java.util.Objects;

public final class ExplosionTerrainPredictor {
    public static List<ExplosionTerrainOutcome> unobserved(
        ExplosionContext context,
        BlockDeltaOverlay current
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(current, "current");
        // ServerExplosion block destruction depends on server RNG. Until block updates arrive,
        // the only safe branch is the current geometry marked explicitly non-exact.
        return List.of(ExplosionTerrainOutcome.unobserved(current));
    }

    private ExplosionTerrainPredictor() {
    }
}
