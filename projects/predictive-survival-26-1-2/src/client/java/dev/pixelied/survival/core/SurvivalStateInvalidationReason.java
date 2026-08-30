package dev.pixelied.survival.core;

import java.util.Set;

/**
 * Typed reasons that justify an urgent same-tick survival reevaluation after vanilla applies
 * client-visible server evidence. Keep this list narrow enough to avoid turning harmless packet
 * spam into unconditional extra analysis passes.
 */
public enum SurvivalStateInvalidationReason {
    ENTITY_ADDED,
    ENTITY_MOTION,
    ENTITY_POSITION,
    ENTITY_REMOVED,
    LOCAL_PLAYER_CORRECTION,
    LOCAL_HEALTH,
    LOCAL_DAMAGE_EVENT,
    LOCAL_TOTEM_POP,
    RELEVANT_ENTITY_METADATA,
    BLOCK_UPDATE,
    INVENTORY_SLOT,
    INVENTORY_CONTENT,
    EQUIPMENT,
    EFFECT_UPDATED,
    EFFECT_REMOVED,
    ATTRIBUTE_UPDATE,
    WORLD_BORDER,
    RESPAWN_RESET,
    DIFFICULTY,
    PLAYER_ABILITIES;

    private static final Set<String> SURVIVAL_RELEVANT_ATTRIBUTES = Set.of(
        "minecraft:armor",
        "minecraft:armor_toughness",
        "minecraft:attack_damage",
        "minecraft:attack_speed",
        "minecraft:burning_time",
        "minecraft:entity_interaction_range",
        "minecraft:fall_damage_multiplier",
        "minecraft:gravity",
        "minecraft:knockback_resistance",
        "minecraft:max_absorption",
        "minecraft:max_health",
        "minecraft:movement_speed",
        "minecraft:oxygen_bonus",
        "minecraft:safe_fall_distance",
        "minecraft:water_movement_efficiency"
    );

    public static boolean isSurvivalRelevantAttribute(String registeredName) {
        return registeredName != null && SURVIVAL_RELEVANT_ATTRIBUTES.contains(registeredName);
    }
}
