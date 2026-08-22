package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public record PlaceAnchor(BlockPos pos) implements CombatAction {
    public PlaceAnchor {
        pos = pos.immutable();
    }

    @Override
    public ActionLegality check(CombatState state) {
        if (!ActionChecks.requireBlockReach(state, pos).legal()) {
            return ActionLegality.denied("anchor position is outside block interaction reach");
        }
        if (state.inventory().count(Items.RESPAWN_ANCHOR) <= 0) {
            return ActionLegality.denied("no respawn anchor resource is known available");
        }
        ActionLegality hand = ActionChecks.requireItemInInteractionHand(
            state,
            Items.RESPAWN_ANCHOR,
            "respawn anchor"
        );
        if (!hand.legal()) {
            return hand;
        }
        ActionLegality replaceable = ActionChecks.requireReplaceablePlacementTarget(state, pos);
        if (!replaceable.legal() || state.anchors().containsKey(pos)) {
            return ActionLegality.denied("anchor position is not replaceable");
        }
        ActionLegality freeSpace = ActionChecks.requireFreeBlockSpace(state, pos);
        if (!freeSpace.legal()) {
            return freeSpace;
        }
        return ActionChecks.requireAdjacentPlacementSupport(state, pos);
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        if (!check(state).legal()) {
            return ActionOutcome.impossible(state);
        }
        LinkedHashMap<BlockPos, AnchorState> anchors = new LinkedHashMap<>(state.anchors());
        anchors.put(pos, new AnchorState(0));
        CombatState next = state
            .withGeometry(state.geometry().withPlaced(pos, Blocks.RESPAWN_ANCHOR.defaultBlockState()))
            .withAnchors(anchors)
            .withInventory(state.inventory().consume(Items.RESPAWN_ANCHOR, 1));
        return ActionOutcome.uncertain(next, java.util.List.of(), false);
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.CLIENT_PREDICTION;
    }
}
