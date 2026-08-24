package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.WorldSnapshot;

import java.util.Locale;
import java.util.Optional;

public enum ProjectileFamily {
    ARROW_LIKE,
    THROWABLE,
    LLAMA_SPIT,
    HURTING_PROJECTILE,
    WIND_CHARGE,
    FIREWORK;

    public static Optional<ProjectileFamily> from(WorldSnapshot.EntitySnapshot entity) {
        String override = entity.properties().get("motion_family");
        if (override != null) {
            try {
                return Optional.of(valueOf(override.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        return switch (entity.typeKey()) {
            case "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident",
                 "minecraft:spear", "minecraft:thrown_spear" -> Optional.of(ARROW_LIKE);
            case "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl",
                 "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion",
                 "minecraft:experience_bottle" -> Optional.of(THROWABLE);
            case "minecraft:llama_spit" -> Optional.of(LLAMA_SPIT);
            case "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball",
                 "minecraft:wither_skull" -> Optional.of(HURTING_PROJECTILE);
            case "minecraft:wind_charge", "minecraft:breeze_wind_charge" -> Optional.of(WIND_CHARGE);
            case "minecraft:firework_rocket" -> Optional.of(FIREWORK);
            default -> Optional.empty();
        };
    }
}
