package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record SpawnCrystalCycle(BlockPos basePos, boolean replaceAfterBreak)
    implements ReactiveActionSpec {
    public SpawnCrystalCycle {
        Objects.requireNonNull(basePos, "basePos");
        basePos = basePos.immutable();
    }

    @Override
    public List<CombatAction> materialize(CombatEvent event) {
        if (!(event instanceof CombatEvent.CrystalSpawned spawned)
            || !spawned.basePos().equals(basePos)) {
            return List.of();
        }
        CombatAction attack = new AttackKnownCrystal(spawned.entityId());
        return replaceAfterBreak
            ? List.of(attack, new PlaceCrystal(basePos))
            : List.of(attack);
    }
}
