package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NonTotemAbsorptionRefreshTest {
    @Test
    void matchingAbsorptionEffectDoesNotSkipUseWhenAbsorptionHeartsWereSpent() {
        EffectInstanceSnapshot absorption = new EffectInstanceSnapshot("minecraft:absorption", 100, 0);
        StatusEffectsSnapshot activeEffect = StatusEffectsSnapshot.none().apply(List.of(absorption));
        SurvivalAction.ApplyEffects action = new SurvivalAction.ApplyEffects(
            activeEffect,
            0f,
            4f,
            "minecraft:golden_apple",
            1,
            true,
            true,
            1d,
            1,
            1,
            Optional.empty(),
            List.of(absorption),
            4f
        );

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            new NonTotemActionExecutor().begin(action, context(player(activeEffect, 0f)))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, waiting.command().orElseThrow(),
            "the item still has to be used because the planned four absorption hearts are not present");
    }

    private static NonTotemExecutionContext context(PlayerSnapshot player) {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(0, new InventorySlotSnapshot(
                0, "minecraft:golden_apple", 123, 1, false,
                Optional.empty(), Optional.empty(), Optional.empty()
            )),
            false
        );
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 0, Map.of(0, 36, 40, 45)),
            new TimingSnapshot(20, 50d, 0d, new TickWindow(21, 21)),
            20,
            false,
            null,
            0,
            true,
            ServerStateEvidenceSnapshot.unknown()
        );
        return new NonTotemExecutionContext(base, player, Set.of());
    }

    private static PlayerSnapshot player(StatusEffectsSnapshot effects, float absorption) {
        return new PlayerSnapshot(
            20f,
            absorption,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            effects,
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 64, 0, 0.6, 65.8, 0.6),
            new Vec3Snapshot(0, 64, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }
}
