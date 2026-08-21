package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

final class ActionChecks {
    static ActionLegality requireBlockReach(CombatState state, BlockPos pos) {
        return state.base().legality().withinBlockReach(pos)
            ? ActionLegality.allowed()
            : ActionLegality.denied("block is outside interaction reach");
    }

    static ActionLegality requireReplaceablePlacementTarget(CombatState state, BlockPos pos) {
        var blockState = state.geometry().getBlockState(pos);
        return blockState.isAir() || blockState.canBeReplaced()
            ? ActionLegality.allowed()
            : ActionLegality.denied("placement target is not replaceable");
    }

    static ActionLegality requireFreeBlockSpace(CombatState state, BlockPos pos) {
        return hasEntityCollision(state.base().legality(), blockBox(pos))
            ? ActionLegality.denied("entity collision blocks placement")
            : ActionLegality.allowed();
    }

    static ActionLegality requireAdjacentPlacementSupport(CombatState state, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            var supportState = state.geometry().getBlockState(pos.relative(direction));
            if (!supportState.isAir() && !supportState.canBeReplaced()) {
                return ActionLegality.allowed();
            }
        }
        return ActionLegality.denied("no adjacent non-replaceable support face is available");
    }

    static boolean hasEntityCollision(LegalitySnapshot legality, AABB box) {
        return legality.hasEntityIntersecting(box);
    }

    static AABB blockBox(BlockPos pos) {
        return new AABB(
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            pos.getX() + 1.0,
            pos.getY() + 1.0,
            pos.getZ() + 1.0
        );
    }

    private ActionChecks() {
    }
}
