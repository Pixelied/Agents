package dev.adrien.crystaloptimizer.v2.debug;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class V3ReplayFixtures {
    static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-00000000a901");
    static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-00000000a902");

    private V3ReplayFixtures() {
    }

    static ReplayFixture popThenFinisher() {
        BlockPos anchorPos = new BlockPos(0, 64, 5);
        KnownCrystal shaped = new KnownCrystal(101, new Vec3(8.5, 65.0, 1.0));
        KnownCrystal greedy = new KnownCrystal(102, new Vec3(7.5, 65.0, 1.0));
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(5.0f).withTotem(TotemState.OFFHAND);
        CombatRegion region = CombatRegion.singleBlock(
            anchorPos,
            Blocks.RESPAWN_ANCHOR.defaultBlockState()
        );
        Map<UUID, CombatantSpatialState> spatial = Map.of(
            SELF,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, -8.0),
                new AABB(0.2, 64.0, -8.3, 0.8, 65.8, -7.7),
                Vec3.ZERO
            ),
            TARGET,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, 1.0),
                new AABB(0.2, 64.0, 0.7, 0.8, 65.8, 1.3),
                Vec3.ZERO
            )
        );
        CombatSnapshot combat = new CombatSnapshot(
            77L,
            SELF,
            region,
            Map.of(SELF, self, TARGET, target),
            List.of(shaped, greedy),
            Map.of(anchorPos, new AnchorState(1)),
            InventoryState.empty(),
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -8.0), 15.0, 15.0, List.of(), false),
            spatial,
            Difficulty.NORMAL
        );
        StrategicSnapshot snapshot = new StrategicSnapshot(
            1L,
            combat.worldRevision(),
            0L,
            0L,
            1L,
            SELF,
            Map.of(TARGET, 1L),
            combat,
            Map.of(),
            Set.of(),
            TargetProtectionPolicyConfig.defaults(),
            TimingSnapshot.empty(1L)
        );
        OptimizerConfig config = new OptimizerConfig(
            true,
            OptimizerStrategy.LETHAL_SPEED,
            15.0,
            0.0f,
            20.0f,
            8.0f,
            true,
            true,
            false,
            RotationMode.ADAPTIVE,
            true
        );
        return new ReplayFixture(snapshot, config, List.of(
            new ReplayEvent(10L, "control.tick", Map.of("phase", "strategic"))
        ));
    }
}
