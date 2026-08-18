package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;

public record ChargeAnchor(BlockPos pos) implements CombatAction {
    public ChargeAnchor {
        pos = pos.immutable();
    }

    @Override
    public ActionLegality check(CombatState state) {
        if (!ActionChecks.requireBlockReach(state, pos).legal()) {
            return ActionLegality.denied("anchor is outside block interaction reach");
        }
        AnchorState anchor = state.anchors().get(pos);
        if (anchor == null || !state.geometry().getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
            return ActionLegality.denied("no known respawn anchor exists at position");
        }
        if (anchor.charges() >= 4) {
            return ActionLegality.denied("respawn anchor is already fully charged");
        }
        if (state.inventory().count(Items.GLOWSTONE) <= 0) {
            return ActionLegality.denied("no glowstone resource is known available");
        }
        if (state.inventory().selectedItem().filter(Items.GLOWSTONE::equals).isEmpty()) {
            return ActionLegality.denied("glowstone is not selected in the real main hand");
        }
        return ActionLegality.allowed();
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        if (!check(state).legal()) {
            return ActionOutcome.impossible(state);
        }
        int nextCharges = state.anchors().get(pos).charges() + 1;
        LinkedHashMap<BlockPos, AnchorState> anchors = new LinkedHashMap<>(state.anchors());
        anchors.put(pos, new AnchorState(nextCharges));
        var blockState = Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, nextCharges);
        CombatState next = state
            .withGeometry(state.geometry().withPlaced(pos, blockState))
            .withAnchors(anchors)
            .withInventory(state.inventory().consume(Items.GLOWSTONE, 1));
        return ActionOutcome.uncertain(next, java.util.List.of(), false);
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.CLIENT_PREDICTION;
    }
}
