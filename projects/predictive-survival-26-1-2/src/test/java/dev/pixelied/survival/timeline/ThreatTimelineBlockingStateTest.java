package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreatTimelineBlockingStateTest {
    @Test
    void disablingBlockedHitStopsShieldForLaterTimelineEvent() {
        PlayerSnapshot player = player(new BlockingSnapshot(
            true, 0f, 5, 5, Optional.of(BlockingProfileSnapshot.fullBlock(100)), 0
        ));
        DamageSourceSnapshot disabling = new DamageSourceSnapshot(
            DamageRange.exact(6f), Set.of(), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 4)), "minecraft:mob_attack", 0f, 0f, 5f
        );
        DamageSourceSnapshot later = new DamageSourceSnapshot(
            DamageRange.exact(6f), Set.of(), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 4)), "minecraft:mob_attack"
        );
        ThreatTimeline timeline = new ThreatTimeline(java.util.List.of(
            event("disable", 0, disabling, true),
            event("later", 21, later, false)
        ));

        TimelineResult result = new ThreatTimelineSimulator().simulate(player, timeline);

        assertEquals(14f, result.finalHealth(), 0.0001f);
    }


    @Test
    void oversizedOverlapGroupFailsClosedWhenOrderingCannotBeExhaustivelyProven() {
        PlayerSnapshot player = player(new BlockingSnapshot(
            true, 0f, 5, 5, Optional.of(BlockingProfileSnapshot.fullBlock(1000)), 0
        ));
        List<ThreatEvent> events = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            DamageSourceSnapshot large = new DamageSourceSnapshot(
                DamageRange.exact(8f), Set.of(DamageFlag.BYPASSES_COOLDOWN), false, 1f, false,
                Optional.of(new Vec3Snapshot(0, 0, 4)), "test:large_" + i
            );
            events.add(event("large-" + i, 0, large, false));
        }
        DamageSourceSnapshot disabling = new DamageSourceSnapshot(
            DamageRange.exact(1f), Set.of(DamageFlag.BYPASSES_COOLDOWN), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 4)), "test:disable", 0f, 0f, 5f
        );
        events.add(event("disable", 0, disabling, true));

        TimelineResult result = new ThreatTimelineSimulator().simulate(player, new ThreatTimeline(events));

        assertFalse(result.survived(), "a non-exhaustive >6-event fallback must never claim guaranteed survival");
    }

    @Test
    void expiredDisableAllowsAlreadyResumedBlockingStateToBeRepresented() {
        BlockingSnapshot blocking = new BlockingSnapshot(
            true, 0f, 5, 5, Optional.of(BlockingProfileSnapshot.fullBlock(100)), 3
        );
        assertEquals(0, blocking.age(3).cooldownTicks());
    }

    private static ThreatEvent event(String id, long tick, DamageSourceSnapshot source, boolean disable) {
        return new ThreatEvent(
            id, ThreatKind.MELEE, new TickWindow(tick, tick), source, Confidence.EXACT,
            source.sourcePosition(), Optional.empty(), false, true, false, disable
        );
    }

    private static PlayerSnapshot player(BlockingSnapshot blocking) {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), blocking, HurtState.unknown(), DeathProtectionSnapshot.none(),
            new AabbSnapshot(-0.3, 0, -0.3, 0.3, 1.8, 0.3), new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0),
            Map.of(), Map.of("head_yaw", "0")
        );
    }
}
