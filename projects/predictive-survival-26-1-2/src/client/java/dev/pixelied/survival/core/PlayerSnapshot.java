package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;

import java.util.Map;
import java.util.Objects;

public record PlayerSnapshot(
    float health,
    float absorption,
    boolean playerInvulnerable,
    boolean abilityInvulnerable,
    boolean deadOrDying,
    DifficultySnapshot difficulty,
    MitigationSnapshot mitigation,
    StatusEffectsSnapshot statusEffects,
    BlockingSnapshot blocking,
    HurtState hurtState,
    DeathProtectionSnapshot deathProtection,
    AabbSnapshot boundingBox,
    Vec3Snapshot position,
    Vec3Snapshot velocity,
    Map<String, String> equipmentItemKeys
) {
    public PlayerSnapshot {
        difficulty = Objects.requireNonNull(difficulty, "difficulty");
        mitigation = Objects.requireNonNull(mitigation, "mitigation");
        statusEffects = Objects.requireNonNull(statusEffects, "statusEffects");
        blocking = Objects.requireNonNull(blocking, "blocking");
        hurtState = Objects.requireNonNull(hurtState, "hurtState");
        deathProtection = Objects.requireNonNull(deathProtection, "deathProtection");
        boundingBox = Objects.requireNonNull(boundingBox, "boundingBox");
        position = Objects.requireNonNull(position, "position");
        velocity = Objects.requireNonNull(velocity, "velocity");
        equipmentItemKeys = Map.copyOf(Objects.requireNonNull(equipmentItemKeys, "equipmentItemKeys"));
        if (health < 0f || absorption < 0f) {
            throw new IllegalArgumentException("health and absorption must be non-negative");
        }
    }
}
