package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.execution.EquipmentAuthorityProjection;
import dev.pixelied.survival.execution.PendingEquipmentMutation;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthorityAwareCandidateDeadlineTest {
    @Test
    void relativeThreatTickIsConvertedToAbsoluteAuthorityDeadline() {
        InventorySlotSnapshot sword = slot(0, "minecraft:diamond_sword", false);
        InventorySlotSnapshot totem = slot(5, "minecraft:totem_of_undying", true);
        InventorySlotSnapshot offhand = slot(40, "minecraft:air", false);
        InventorySnapshot confirmedInventory = new InventorySnapshot(
            5,
            Map.of(0, sword, 5, totem, 40, offhand),
            false
        );
        EquipmentAuthorityProjection equipment = new EquipmentAuthorityProjection(
            5,
            totem,
            offhand,
            List.of(new PendingEquipmentMutation(
                SurvivalAction.Hand.MAIN_HAND,
                totem,
                sword,
                new TickWindow(102, 104),
                PendingEquipmentMutation.Origin.RESTORE,
                1L
            )),
            1L
        );

        List<SurvivalAction> candidates = new AuthorityAwareCandidateGenerator().generate(
            contextAtTick100(),
            timelineWithRelativeImpact(3),
            confirmedInventory,
            new MenuSlotMap(0, 4, Map.of(0, 36, 5, 41, 40, 45)),
            RescuePolicy.smartDefaults(),
            equipment
        );

        SurvivalAction.EquipDeathProtection equip = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            candidates.stream()
                .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "absolute tick 103 is inside the restore authority window and must re-arm"
                ))
        );
        assertEquals(new DeathProtectionRoute.HotbarSelect(5), equip.sourceItem().orElseThrow().route());
    }

    private static PredictionContext contextAtTick100() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(100, 100, 10, new TickWindow(102, 104)),
            EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timelineWithRelativeImpact(long relativeTick) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:absolute-authority-deadline"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "absolute-authority-deadline",
            ThreatKind.OTHER,
            new TickWindow(relativeTick, relativeTick),
            damage,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        )));
    }

    private static InventorySlotSnapshot slot(int index, String key, boolean protection) {
        return new InventorySlotSnapshot(index, key, protection ? 1 : ("minecraft:air".equals(key) ? 0 : 1), protection);
    }
}
