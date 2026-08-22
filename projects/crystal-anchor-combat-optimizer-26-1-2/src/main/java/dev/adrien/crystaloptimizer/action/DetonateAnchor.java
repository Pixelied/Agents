package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public record DetonateAnchor(BlockPos pos) implements CombatAction {
    public DetonateAnchor {
        pos = pos.immutable();
    }

    @Override
    public ActionLegality check(CombatState state) {
        if (!ActionChecks.requireBlockReach(state, pos).legal()) {
            return ActionLegality.denied("anchor is outside block interaction reach");
        }
        AnchorState anchor = state.anchors().get(pos);
        if (anchor == null || !anchor.charged() || !state.geometry().getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
            return ActionLegality.denied("no charged known respawn anchor exists at position");
        }
        if (state.base().legality().respawnAnchorWorks()) {
            return ActionLegality.denied("respawn anchors work normally in this environment");
        }
        if (anchor.charges() < 4 && state.inventory().hasItemInEitherHand(Items.GLOWSTONE)) {
            return ActionLegality.denied("glowstone in a hand would charge the anchor instead of detonating it");
        }
        return ActionLegality.allowed();
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        if (!check(state).legal()) {
            return ActionOutcome.impossible(state);
        }
        LinkedHashMap<BlockPos, AnchorState> anchors = new LinkedHashMap<>(state.anchors());
        anchors.remove(pos);
        CombatState next = state
            .withGeometry(state.geometry().withRemoved(pos))
            .withAnchors(anchors);
        return ActionOutcome.uncertain(
            next,
            List.of(ExplosionContext.anchor(pos, false)),
            false
        );
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.NONE;
    }
}
