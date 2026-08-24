package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingProfileSemanticsTest {
    private final DamageSimulator simulator = new DamageSimulator();

    @Test
    void reductionHonorsHorizontalAngleAndDamageType() {
        BlockingProfileSnapshot profile = new BlockingProfileSnapshot(
            List.of(new BlockingProfileSnapshot.DamageReduction(90f, Optional.of(Set.of("minecraft:mob_attack")), 0f, 0.5f)),
            new BlockingProfileSnapshot.ItemDamageFunction(0f, 0f, 0f),
            Set.of(),
            1f,
            100
        );
        PlayerSnapshot player = player(new BlockingSnapshot(true, 0f, 5, 5, Optional.of(profile), 0));

        DamageResult front = simulator.simulate(player, source("minecraft:mob_attack", 10f, new Vec3Snapshot(0, 0, 4)));
        DamageResult back = simulator.simulate(player, source("minecraft:mob_attack", 10f, new Vec3Snapshot(0, 0, -4)));
        DamageResult wrongType = simulator.simulate(player, source("minecraft:explosion", 10f, new Vec3Snapshot(0, 0, 4)));

        assertEquals(5f, front.trace().after(DamageStage.BLOCKING), 0.0001f);
        assertEquals(10f, back.trace().after(DamageStage.BLOCKING), 0.0001f);
        assertEquals(10f, wrongType.trace().after(DamageStage.BLOCKING), 0.0001f);
    }

    @Test
    void componentBypassPreventsBlockingEvenWithoutGlobalShieldTag() {
        BlockingProfileSnapshot profile = new BlockingProfileSnapshot(
            List.of(new BlockingProfileSnapshot.DamageReduction(90f, Optional.empty(), 0f, 1f)),
            new BlockingProfileSnapshot.ItemDamageFunction(0f, 0f, 0f),
            Set.of("minecraft:magic"),
            1f,
            100
        );
        DamageResult result = simulator.simulate(
            player(new BlockingSnapshot(true, 0f, 5, 5, Optional.of(profile), 0)),
            source("minecraft:magic", 8f, new Vec3Snapshot(0, 0, 4))
        );
        assertEquals(8f, result.trace().after(DamageStage.BLOCKING), 0.0001f);
    }

    @Test
    void itemDamageBreaksBlockingItemAfterCurrentHit() {
        BlockingProfileSnapshot profile = new BlockingProfileSnapshot(
            List.of(new BlockingProfileSnapshot.DamageReduction(90f, Optional.empty(), 0f, 1f)),
            new BlockingProfileSnapshot.ItemDamageFunction(3f, 1f, 1f),
            Set.of(),
            1f,
            5
        );
        PlayerSnapshot player = player(new BlockingSnapshot(true, 0f, 5, 5, Optional.of(profile), 0));
        DamageSourceSnapshot source = source("minecraft:mob_attack", 5f, new Vec3Snapshot(0, 0, 4));

        DamageResult first = simulator.simulate(player, source);
        assertEquals(0f, first.trace().after(DamageStage.BLOCKING), 0.0001f);
        assertFalse(first.after().blocking().active(), "the shield breaks after blocking the current hit");
        assertEquals(0, first.after().blocking().profile().orElseThrow().remainingDurability());

        DamageResult second = simulator.simulate(withNoCooldown(first.after()), source);
        assertEquals(5f, second.trace().after(DamageStage.BLOCKING), 0.0001f);
    }

    @Test
    void activeCooldownPreventsBlocking() {
        BlockingProfileSnapshot profile = BlockingProfileSnapshot.fullBlock(100);
        DamageResult result = simulator.simulate(
            player(new BlockingSnapshot(true, 0f, 20, 5, Optional.of(profile), 40)),
            source("minecraft:mob_attack", 6f, new Vec3Snapshot(0, 0, 4))
        );
        assertEquals(6f, result.trace().after(DamageStage.BLOCKING), 0.0001f);
        assertFalse(result.after().blocking().active());
    }

    @Test
    void warmupAdjustmentPreservesDataDrivenProfile() {
        BlockingProfileSnapshot profile = BlockingProfileSnapshot.fullBlock(42);
        BlockingSnapshot snapshot = new BlockingSnapshot(true, 0f, 2, 5, Optional.of(profile), 0);

        BlockingSnapshot adjusted = snapshot.withElapsedUseTicks(4);

        assertEquals(42, adjusted.profile().orElseThrow().remainingDurability());
        assertEquals(4, adjusted.elapsedUseTicks());
    }

    @Test
    void fullyBlockedHitIsRejectedButStillRefreshesHurtState() {
        BlockingProfileSnapshot profile = BlockingProfileSnapshot.fullBlock(100);
        DamageResult result = simulator.simulate(
            player(new BlockingSnapshot(true, 0f, 5, 5, Optional.of(profile), 0)),
            source("minecraft:mob_attack", 6f, new Vec3Snapshot(0, 0, 4))
        );
        assertTrue(result.rejected());
        assertEquals(20, result.after().hurtState().invulnerableTime());
        assertEquals(0f, result.after().hurtState().lastHurt().max(), 0.0001f);
    }

    private static DamageSourceSnapshot source(String key, float damage, Vec3Snapshot position) {
        return new DamageSourceSnapshot(
            DamageRange.exact(damage), Set.of(), false, 1f, false, Optional.of(position), key
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

    private static PlayerSnapshot withNoCooldown(PlayerSnapshot player) {
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(), player.deadOrDying(),
            player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking(), HurtState.unknown(), player.deathProtection(),
            player.boundingBox(), player.position(), player.velocity(), player.equipmentItemKeys(), player.stateProperties()
        );
    }
}
