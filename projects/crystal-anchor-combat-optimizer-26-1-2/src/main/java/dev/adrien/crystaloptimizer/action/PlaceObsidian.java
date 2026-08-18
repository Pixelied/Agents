package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public record PlaceObsidian(BlockPos pos) implements CombatAction {
    public PlaceObsidian {
        pos = pos.immutable();
    }

    @Override
    public ActionLegality check(CombatState state) {
        if (!ActionChecks.requireBlockReach(state, pos).legal()) {
            return ActionLegality.denied("obsidian position is outside block interaction reach");
        }
        if (state.inventory().count(Items.OBSIDIAN) <= 0) {
            return ActionLegality.denied("no obsidian resource is known available");
        }
        if (state.inventory().selectedItem().filter(Items.OBSIDIAN::equals).isEmpty()) {
            return ActionLegality.denied("obsidian is not selected in the real main hand");
        }
        if (!state.geometry().getBlockState(pos).isAir()) {
            return ActionLegality.denied("support position is not conservatively empty");
        }
        return ActionChecks.requireFreeBlockSpace(state, pos);
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        if (!check(state).legal()) {
            return ActionOutcome.impossible(state);
        }
        CombatState next = state
            .withGeometry(state.geometry().withPlaced(pos, Blocks.OBSIDIAN.defaultBlockState()))
            .withInventory(state.inventory().consume(Items.OBSIDIAN, 1));
        return ActionOutcome.uncertain(next, java.util.List.of(), false);
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.CLIENT_PREDICTION;
    }
}
