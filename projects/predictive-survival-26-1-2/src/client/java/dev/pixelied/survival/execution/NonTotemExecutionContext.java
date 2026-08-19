package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;
import java.util.Set;

public record NonTotemExecutionContext(
    ExecutionContext base,
    PlayerSnapshot player,
    Set<SurvivalAction.BlockTarget> confirmedBlocks
) {
    public NonTotemExecutionContext {
        base = Objects.requireNonNull(base, "base");
        player = Objects.requireNonNull(player, "player");
        confirmedBlocks = Set.copyOf(Objects.requireNonNull(confirmedBlocks, "confirmedBlocks"));
    }
}
