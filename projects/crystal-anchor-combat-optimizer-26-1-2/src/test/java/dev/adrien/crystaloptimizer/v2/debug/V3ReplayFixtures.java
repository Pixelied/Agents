package dev.adrien.crystaloptimizer.v2.debug;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingDistribution;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
            spatial(new Vec3(0.5, 64.0, -8.0)),
            TARGET,
            spatial(new Vec3(0.5, 64.0, 1.0))
        );
        CombatSnapshot combat = combat(
            77L,
            SELF,
            region,
            Map.of(SELF, self, TARGET, target),
            List.of(shaped, greedy),
            Map.of(anchorPos, new AnchorState(1)),
            InventoryState.empty(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -8.0), 15.0, 15.0, List.of(), false),
            spatial
        );
        return fixture(
            combat,
            Map.of(TARGET, 1L),
            Map.of(),
            TimingSnapshot.empty(1L),
            config(true, true, 15.0),
            "pop-then-finisher"
        );
    }

    static ReplayFixture candidateBudgetAnchorStarvation() {
        BlockPos anchorPos = new BlockPos(0, 63, 3);
        LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                BlockPos pos = new BlockPos(x, 63, z);
                if (!pos.equals(anchorPos)) {
                    blocks.put(pos, Blocks.OBSIDIAN.defaultBlockState());
                }
            }
        }
        blocks.put(anchorPos, Blocks.RESPAWN_ANCHOR.defaultBlockState());
        CombatRegion region = CombatRegion.of(blocks, Map.of());
        InventoryState inventory = inventory(Items.END_CRYSTAL, 64);
        CombatSnapshot combat = combat(
            101L,
            SELF,
            region,
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            List.of(),
            Map.of(anchorPos, new AnchorState(1)),
            inventory,
            new LegalitySnapshot(new Vec3(0.5, 65.5, -8.0), 20.0, 20.0, List.of(), false),
            Map.of(
                SELF, spatial(new Vec3(0.5, 64.0, -8.0)),
                TARGET, spatial(new Vec3(0.5, 64.0, 2.4))
            )
        );
        return fixture(
            combat,
            Map.of(TARGET, 4L),
            Map.of(),
            TimingSnapshot.empty(1_000L),
            config(true, true, 20.0),
            "candidate-budget-anchor-starvation"
        );
    }

    static ReplayFixture fourthTargetLethal() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-00000000a911");
        UUID second = UUID.fromString("00000000-0000-0000-0000-00000000a912");
        UUID third = UUID.fromString("00000000-0000-0000-0000-00000000a913");
        UUID fourth = UUID.fromString("00000000-0000-0000-0000-00000000a914");
        BlockPos anchorPos = new BlockPos(7, 63, 0);
        CombatRegion region = CombatRegion.singleBlock(
            anchorPos,
            Blocks.RESPAWN_ANCHOR.defaultBlockState()
        );
        LinkedHashMap<UUID, SimCombatant> combatants = new LinkedHashMap<>();
        combatants.put(SELF, SimCombatant.testPlayer(20.0f));
        combatants.put(first, SimCombatant.testPlayer(20.0f));
        combatants.put(second, SimCombatant.testPlayer(20.0f));
        combatants.put(third, SimCombatant.testPlayer(20.0f));
        combatants.put(fourth, SimCombatant.testPlayer(20.0f));
        LinkedHashMap<UUID, CombatantSpatialState> spatial = new LinkedHashMap<>();
        spatial.put(SELF, spatial(new Vec3(0.5, 64.0, 0.5)));
        spatial.put(first, spatial(new Vec3(1.5, 64.0, 3.5)));
        spatial.put(second, spatial(new Vec3(-2.5, 64.0, 3.5)));
        spatial.put(third, spatial(new Vec3(-4.5, 64.0, 0.5)));
        spatial.put(fourth, spatial(new Vec3(7.5, 64.0, 0.5)));
        CombatSnapshot combat = combat(
            102L,
            SELF,
            region,
            combatants,
            List.of(),
            Map.of(anchorPos, new AnchorState(1)),
            InventoryState.empty(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, 0.5), 15.0, 15.0, List.of(), false),
            spatial
        );
        return fixture(
            combat,
            Map.of(first, 1L, second, 1L, third, 1L, fourth, 1L),
            Map.of(),
            TimingSnapshot.empty(2_000L),
            config(false, true, 15.0),
            "fourth-target-lethal"
        );
    }

    static ReplayFixture protectedWindowSingleApplication() {
        SimCombatant target = SimCombatant.testPlayer(20.0f)
            .withHurtWindow(new HurtWindowState(15, 10.0f, true));
        KnownCrystal crystal = new KnownCrystal(501, new Vec3(9.5, 65.0, 0.5));
        CombatSnapshot combat = combat(
            103L,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, SimCombatant.testPlayer(20.0f), TARGET, target),
            List.of(crystal),
            Map.of(),
            InventoryState.empty(),
            new LegalitySnapshot(new Vec3(9.5, 65.5, -8.0), 15.0, 15.0, List.of(), false),
            Map.of(
                SELF, spatial(new Vec3(9.5, 64.0, -8.0)),
                TARGET, spatial(new Vec3(0.5, 64.0, 0.5))
            )
        );
        return fixture(
            combat,
            Map.of(TARGET, 9L),
            Map.of(),
            TimingSnapshot.empty(3_000L),
            config(true, false, 15.0),
            "protected-window-single-application"
        );
    }

    static ReplayFixture breakRemovePlaceContinuation() {
        BlockPos base = new BlockPos(1, 63, 0);
        KnownCrystal crystal = new KnownCrystal(712, new Vec3(1.5, 64.0, 0.5));
        CombatSnapshot combat = combat(
            104L,
            SELF,
            CombatRegion.singleBlock(base, Blocks.OBSIDIAN.defaultBlockState()),
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            List.of(crystal),
            Map.of(),
            inventory(Items.END_CRYSTAL, 16),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -3.0), 10.0, 10.0, List.of(), false),
            Map.of(
                SELF, spatial(new Vec3(0.5, 64.0, -3.0)),
                TARGET, spatial(new Vec3(3.5, 64.0, 0.5))
            )
        );
        StrategicSnapshot snapshot = strategic(
            combat,
            Map.of(TARGET, 2L),
            Map.of(),
            TimingSnapshot.empty(4_000L)
        );
        return new ReplayFixture(snapshot, config(true, false, 10.0), List.of(
            new ReplayEvent(10L, "combat.crystal_removed", Map.of(
                "entityId", "712",
                "baseX", Integer.toString(base.getX()),
                "baseY", Integer.toString(base.getY()),
                "baseZ", Integer.toString(base.getZ())
            )),
            new ReplayEvent(20L, "control.tick", Map.of("scenario", "break-remove-place-continuation"))
        ));
    }

    static ReplayFixture predictedStrafePlacement() {
        long capturedAt = 1_100_000_000L;
        BlockPos currentBase = new BlockPos(2, 63, 0);
        BlockPos futureBase = new BlockPos(5, 63, 0);
        CombatRegion region = CombatRegion.of(
            Map.of(
                currentBase, Blocks.OBSIDIAN.defaultBlockState(),
                futureBase, Blocks.OBSIDIAN.defaultBlockState()
            ),
            Map.of()
        );
        CombatSnapshot combat = combat(
            105L,
            SELF,
            region,
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            List.of(),
            Map.of(),
            inventory(Items.END_CRYSTAL, 16),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -4.0), 12.0, 12.0, List.of(), false),
            Map.of(
                SELF, spatial(new Vec3(0.5, 64.0, -4.0)),
                TARGET, spatial(new Vec3(2.5, 64.0, 0.5))
            )
        );
        List<MovementSample> history = List.of(
            new MovementSample(1_000_000_000L, new Vec3(1.7, 64.0, 0.5), new Vec3(0.4, 0.0, 0.0)),
            new MovementSample(1_050_000_000L, new Vec3(2.1, 64.0, 0.5), new Vec3(0.4, 0.0, 0.0)),
            new MovementSample(capturedAt, new Vec3(2.5, 64.0, 0.5), new Vec3(0.4, 0.0, 0.0))
        );
        EnumMap<TimingTransition, TimingDistribution> timing = new EnumMap<>(TimingTransition.class);
        timing.put(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            new TimingDistribution(8, 150.0, 220.0, 12.0, 1.0, capturedAt)
        );
        StrategicSnapshot snapshot = strategic(
            combat,
            Map.of(TARGET, 5L),
            Map.of(TARGET, history),
            new TimingSnapshot(capturedAt, timing)
        );
        return new ReplayFixture(snapshot, config(true, false, 12.0), List.of(
            new ReplayEvent(10L, "control.tick", Map.of("scenario", "predicted-strafe-placement"))
        ));
    }

    static Map<String, ReplayFixture> checkedInFixtures() {
        LinkedHashMap<String, ReplayFixture> result = new LinkedHashMap<>();
        result.put("candidate-budget-anchor-starvation.json", candidateBudgetAnchorStarvation());
        result.put("fourth-target-lethal.json", fourthTargetLethal());
        result.put("protected-window-single-application.json", protectedWindowSingleApplication());
        result.put("break-remove-place-continuation.json", breakRemovePlaceContinuation());
        result.put("predicted-strafe-placement.json", predictedStrafePlacement());
        return Map.copyOf(result);
    }

    private static ReplayFixture fixture(
        CombatSnapshot combat,
        Map<UUID, Long> targetRevisions,
        Map<UUID, List<MovementSample>> movementHistory,
        TimingSnapshot timing,
        OptimizerConfig config,
        String scenario
    ) {
        return new ReplayFixture(
            strategic(combat, targetRevisions, movementHistory, timing),
            config,
            List.of(new ReplayEvent(10L, "control.tick", Map.of("scenario", scenario)))
        );
    }

    private static StrategicSnapshot strategic(
        CombatSnapshot combat,
        Map<UUID, Long> targetRevisions,
        Map<UUID, List<MovementSample>> movementHistory,
        TimingSnapshot timing
    ) {
        return new StrategicSnapshot(
            1L,
            combat.worldRevision(),
            0L,
            0L,
            timing.capturedAtNanos(),
            SELF,
            targetRevisions,
            combat,
            movementHistory,
            Set.of(),
            TargetProtectionPolicyConfig.defaults(),
            timing
        );
    }

    private static CombatSnapshot combat(
        long worldRevision,
        UUID selfId,
        CombatRegion region,
        Map<UUID, SimCombatant> combatants,
        List<KnownCrystal> crystals,
        Map<BlockPos, AnchorState> anchors,
        InventoryState inventory,
        LegalitySnapshot legality,
        Map<UUID, CombatantSpatialState> spatial
    ) {
        return new CombatSnapshot(
            worldRevision,
            selfId,
            region,
            combatants,
            crystals,
            anchors,
            inventory,
            TimingState.unknown(),
            legality,
            spatial,
            Difficulty.NORMAL
        );
    }

    private static InventoryState inventory(Item item, int count) {
        return new InventoryState(
            0,
            Map.of(item, count),
            Map.of(0, item),
            Map.of(0, count),
            Optional.empty()
        );
    }

    private static CombatantSpatialState spatial(Vec3 position) {
        return new CombatantSpatialState(
            position,
            new AABB(
                position.x - 0.3,
                position.y,
                position.z - 0.3,
                position.x + 0.3,
                position.y + 1.8,
                position.z + 0.3
            ),
            Vec3.ZERO
        );
    }

    private static OptimizerConfig config(boolean crystals, boolean anchors, double targetRange) {
        return new OptimizerConfig(
            true,
            OptimizerStrategy.LETHAL_SPEED,
            targetRange,
            0.0f,
            20.0f,
            8.0f,
            crystals,
            anchors,
            false,
            RotationMode.ADAPTIVE,
            true
        );
    }
}
