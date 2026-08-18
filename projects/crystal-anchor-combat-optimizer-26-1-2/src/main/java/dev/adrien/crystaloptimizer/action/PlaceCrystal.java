package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public record PlaceCrystal(BlockPos basePos) implements CombatAction {
    public PlaceCrystal {
        basePos = basePos.immutable();
    }

    @Override
    public ActionLegality check(CombatState state) {
        if (!ActionChecks.requireBlockReach(state, basePos).legal()) {
            return ActionLegality.denied("crystal base is outside block interaction reach");
        }
        if (state.inventory().count(Items.END_CRYSTAL) <= 0) {
            return ActionLegality.denied("no end crystal resource is known available");
        }
        if (state.inventory().selectedItem().filter(Items.END_CRYSTAL::equals).isEmpty()) {
            return ActionLegality.denied("end crystal is not selected in the real main hand");
        }
        var baseState = state.geometry().getBlockState(basePos);
        if (!baseState.is(Blocks.OBSIDIAN) && !baseState.is(Blocks.BEDROCK)) {
            return ActionLegality.denied("end crystal base is not obsidian or bedrock");
        }
        BlockPos above = basePos.above();
        if (!state.geometry().getBlockState(above).isAir()) {
            return ActionLegality.denied("block immediately above crystal base is not empty");
        }
        AABB crystalBox = new AABB(
            above.getX(),
            above.getY(),
            above.getZ(),
            above.getX() + 1.0,
            above.getY() + 2.0,
            above.getZ() + 1.0
        );
        if (ActionChecks.hasEntityCollision(state.base().legality(), crystalBox)) {
            return ActionLegality.denied("entity occupies the 26.1.2 crystal placement AABB");
        }
        return ActionLegality.allowed();
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        if (!check(state).legal()) {
            return ActionOutcome.impossible(state);
        }
        CombatState next = state.withInventory(state.inventory().consume(Items.END_CRYSTAL, 1));
        return ActionOutcome.uncertain(next, java.util.List.of(), true);
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.CLIENT_PREDICTION;
    }
}
