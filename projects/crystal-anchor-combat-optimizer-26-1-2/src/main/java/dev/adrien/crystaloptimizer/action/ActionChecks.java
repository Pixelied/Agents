package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

final class ActionChecks {
    static ActionLegality requireBlockReach(CombatState state, BlockPos pos) {
        return state.base().legality().withinBlockReach(pos)
            ? ActionLegality.allowed()
            : ActionLegality.denied("block is outside interaction reach");
    }

    static ActionLegality requireFreeBlockSpace(CombatState state, BlockPos pos) {
        return hasEntityCollision(state.base().legality(), blockBox(pos))
            ? ActionLegality.denied("entity collision blocks placement")
            : ActionLegality.allowed();
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
