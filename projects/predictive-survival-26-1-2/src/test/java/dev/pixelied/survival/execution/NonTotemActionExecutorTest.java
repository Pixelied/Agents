package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.ConsumableSurvivalSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.planner.SurvivalCandidateGenerator;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NonTotemActionExecutorTest {
    @Test
    void coverConfirmsOnlyAfterTargetBlockIsObserved() {
        SurvivalAction.BlockTarget target = new SurvivalAction.BlockTarget(2, 64, 0, "minecraft:obsidian");
        SurvivalAction.PlaceCover action = new SurvivalAction.PlaceCover(
            Map.of("crystal", DamageRange.exact(2f)), Optional.of(target),
            1, true, true, 1d, 0, 1
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        ExecutionStatus begin = executor.begin(action, context("minecraft:obsidian", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 10));
        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(ExecutionStatus.WaitingForServer.class, begin);
        assertInstanceOf(ExecutionCommand.PlaceBlock.class, waiting.command().orElseThrow());

        assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context("minecraft:obsidian", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 11))
        );
        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context("minecraft:obsidian", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(target), 12))
        );
    }

    @Test
    void equipmentUseWaitsForObservedEquipmentState() {
        SurvivalAction.SwapEquipment action = new SurvivalAction.SwapEquipment(
            new MitigationSnapshot(20f, 8f, 1f, 0, false, 0),
            Map.of("chest", "minecraft:netherite_chestplate"),
            1, true, true, 1d, 0, 1
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context("minecraft:netherite_chestplate", player(Map.of("chest", "minecraft:elytra"), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 20))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, waiting.command().orElseThrow());

        assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context("minecraft:netherite_chestplate", player(Map.of("chest", "minecraft:elytra"), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 21))
        );
        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context("minecraft:netherite_chestplate", player(Map.of("chest", "minecraft:netherite_chestplate"), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 22))
        );
    }

    @Test
    void effectUseWaitsForObservedEffect() {
        StatusEffectsSnapshot fireResistance = new StatusEffectsSnapshot(true, -1);
        SurvivalAction.ApplyEffects action = new SurvivalAction.ApplyEffects(
            fireResistance, 0f, 0f, "minecraft:potion",
            1, true, true, 1d, 1, 1
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context("minecraft:potion", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 30))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, waiting.command().orElseThrow());

        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context("minecraft:potion", player(Map.of(), fireResistance, new Vec3Snapshot(0, 64, 0)), Set.of(), 31))
        );
    }


    @Test
    void sameItemKeyUsesExactHeldStackThatProducedCandidate() {
        InventorySlotSnapshot mainPotion = new InventorySlotSnapshot(
            0, "minecraft:potion", 111, 1, false,
            Optional.of(new ConsumableSurvivalSnapshot(
                1, true, List.of(new EffectInstanceSnapshot("minecraft:resistance", 600, 0))
            )),
            Optional.empty(), Optional.empty()
        );
        InventorySlotSnapshot offhandPotion = new InventorySlotSnapshot(
            40, "minecraft:potion", 222, 1, false,
            Optional.of(new ConsumableSurvivalSnapshot(
                1, true, List.of(new EffectInstanceSnapshot("minecraft:fire_resistance", 600, 0))
            )),
            Optional.empty(), Optional.empty()
        );
        InventorySnapshot inventory = new InventorySnapshot(0, Map.of(0, mainPotion, 40, offhandPotion), false);
        MenuSlotMap menu = new MenuSlotMap(0, 0, Map.of(0, 36, 40, 45));
        PlayerSnapshot basePlayer = player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0));
        PredictionContext prediction = new PredictionContext(
            basePlayer,
            WorldSnapshot.empty(),
            new TimingSnapshot(10, 50, 0, new TickWindow(11, 11)),
            EngineLimits.defaults()
        );
        SurvivalAction.ApplyEffects fireAction = assertInstanceOf(
            SurvivalAction.ApplyEffects.class,
            new SurvivalCandidateGenerator().generate(prediction, new ThreatTimeline(List.of()), inventory, menu).stream()
                .filter(SurvivalAction.ApplyEffects.class::isInstance)
                .map(SurvivalAction.ApplyEffects.class::cast)
                .filter(action -> action.statusEffectsAfter().fireResistance())
                .findFirst()
                .orElseThrow()
        );
        NonTotemExecutionContext execution = new NonTotemExecutionContext(
            new ExecutionContext(
                inventory, menu,
                new TimingSnapshot(10, 50, 0, new TickWindow(11, 11)),
                10, false, null, 0, true
            ),
            basePlayer,
            Set.of()
        );

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            new NonTotemActionExecutor().begin(fireAction, execution)
        );
        ExecutionCommand.UseItem use = assertInstanceOf(ExecutionCommand.UseItem.class, waiting.command().orElseThrow());

        assertEquals(SurvivalAction.Hand.OFF_HAND, use.hand(),
            "same registry key is not enough; execution must preserve the candidate's exact held stack identity");
    }

    @Test
    void oneTickExistingEffectDoesNotConfirmLongRefresh() {
        StatusEffectsSnapshot oneTickFireResistance = new StatusEffectsSnapshot(
            true,
            -1,
            Map.of("minecraft:fire_resistance", new EffectInstanceSnapshot("minecraft:fire_resistance", 1, 0))
        );
        StatusEffectsSnapshot refreshed = new StatusEffectsSnapshot(
            true,
            -1,
            Map.of("minecraft:fire_resistance", new EffectInstanceSnapshot("minecraft:fire_resistance", 600, 0))
        );
        SurvivalAction.ApplyEffects action = new SurvivalAction.ApplyEffects(
            refreshed, 0f, 0f, "minecraft:potion",
            1, true, true, 1d, 1, 1
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        executor.begin(action, context(
            "minecraft:potion",
            player(Map.of(), oneTickFireResistance, new Vec3Snapshot(0, 64, 0)),
            Set.of(),
            30
        ));

        assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(
                "minecraft:potion",
                player(Map.of(), oneTickFireResistance, new Vec3Snapshot(0, 64, 0)),
                Set.of(),
                31
            )),
            "matching amplifier with an expiring duration must not masquerade as the refreshed effect"
        );
    }

    @Test
    void pearlRescueWaitsForObservedTeleport() {
        Vec3Snapshot target = new Vec3Snapshot(8, 64, 0);
        SurvivalAction.PearlRescue action = new SurvivalAction.PearlRescue(
            Set.of("fall"), 5, Optional.of(target),
            1, true, true, 0.95d, 1, 2
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context("minecraft:ender_pearl", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 40))
        );
        assertInstanceOf(ExecutionCommand.AimAndUseItem.class, waiting.command().orElseThrow());

        assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context("minecraft:ender_pearl", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 41))
        );
        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context("minecraft:air", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(8, 64, 0)), Set.of(), 42))
        );
    }

    @Test
    void relocationWaitsForObservedPosition() {
        Vec3Snapshot target = new Vec3Snapshot(3, 64, 0);
        SurvivalAction.Relocate action = new SurvivalAction.Relocate(
            target, Set.of("projectile"), 1, true, true, 1d, 0, 1
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context("minecraft:air", player(Map.of(), StatusEffectsSnapshot.none(), new Vec3Snapshot(0, 64, 0)), Set.of(), 50))
        );
        assertInstanceOf(ExecutionCommand.MoveToward.class, waiting.command().orElseThrow());

        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context("minecraft:air", player(Map.of(), StatusEffectsSnapshot.none(), target), Set.of(), 51))
        );
    }

    private static NonTotemExecutionContext context(String selectedItem, PlayerSnapshot player, Set<SurvivalAction.BlockTarget> blocks, long tick) {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(0, new InventorySlotSnapshot(0, selectedItem, selectedItem.equals("minecraft:air") ? 0 : 1, false)),
            false
        );
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 0, Map.of(0, 36, 40, 45)),
            new TimingSnapshot(tick, 50, 0, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true
        );
        return new NonTotemExecutionContext(base, player, blocks);
    }

    private static PlayerSnapshot player(Map<String, String> equipment, StatusEffectsSnapshot effects, Vec3Snapshot position) {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), effects, BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(position.x(), position.y(), position.z(), position.x() + 0.6, position.y() + 1.8, position.z() + 0.6),
            position, new Vec3Snapshot(0, 0, 0), equipment
        );
    }
}
