package dev.pixelied.survival.core;

import java.util.LinkedHashMap;
import java.util.Map;

final class MinecraftExplosionSnapshotRules {
    private static final float DEFAULT_TNT_RADIUS = 4.0f;
    private static final float MAX_HIDDEN_TNT_RADIUS = 128.0f;
    private static final float DEFAULT_MINECART_RADIUS_MIN = 4.0f;
    private static final float DEFAULT_MINECART_RADIUS_MAX = 11.5f;
    private static final float MAX_HIDDEN_MINECART_RADIUS = 1088.0f;
    private static final int DEFAULT_CREEPER_VISUAL_FUSE = 28;

    private MinecraftExplosionSnapshotRules() {
    }

    static Map<String, String> primedTnt(int observedFuse) {
        Map<String, String> properties = hiddenRadius(DEFAULT_TNT_RADIUS, MAX_HIDDEN_TNT_RADIUS);
        properties.put("fuse_ticks", Integer.toString(Math.max(0, observedFuse)));
        properties.put("countdown_server_synchronized", "true");
        explosionDefaults(properties);
        return Map.copyOf(properties);
    }

    static Map<String, String> tntMinecart(boolean primed, int observedFuse) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("tnt_minecart", "true");
        properties.put("tnt_minecart_primed", Boolean.toString(primed));
        properties.put("explosion_radius_default_min", Float.toString(DEFAULT_MINECART_RADIUS_MIN));
        properties.put("explosion_radius_default_max", Float.toString(DEFAULT_MINECART_RADIUS_MAX));
        properties.put("explosion_radius_hidden_min", "0.0");
        properties.put("explosion_radius_hidden_max", Float.toString(MAX_HIDDEN_MINECART_RADIUS));
        properties.put("server_hidden_explosion_power", "true");
        if (primed) {
            properties.put("fuse_ticks_min", "0");
            properties.put("fuse_ticks_max", Integer.toString(Math.max(0, observedFuse)));
        }
        explosionDefaults(properties);
        return Map.copyOf(properties);
    }

    static boolean creeperRelevant(boolean ignited, int swellDir) {
        return ignited || swellDir > 0;
    }

    static Map<String, String> creeper(boolean powered, boolean ignited, int swellDir, float observedProgress) {
        float defaultRadius = powered ? 6.0f : 3.0f;
        float hiddenMax = powered ? 254.0f : 127.0f;
        Map<String, String> properties = hiddenRadius(defaultRadius, hiddenMax);
        float progress = Float.isFinite(observedProgress)
            ? Math.max(0.0f, Math.min(1.0f, observedProgress))
            : 0.0f;
        int conservativeRemaining = Math.max(
            0,
            (int) Math.floor((1.0f - progress) * DEFAULT_CREEPER_VISUAL_FUSE)
        );
        properties.put("fuse_ticks_min", "0");
        properties.put("fuse_ticks_max", Integer.toString(conservativeRemaining));
        properties.put("creeper_ignited", Boolean.toString(ignited));
        properties.put("creeper_swell_dir", Integer.toString(swellDir));
        explosionDefaults(properties);
        return Map.copyOf(properties);
    }

    static Map<String, String> witherSpawn(int invulnerableTicks) {
        if (invulnerableTicks <= 0) return Map.of();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("explosion_radius", "7.0");
        properties.put("fuse_ticks", Integer.toString(invulnerableTicks));
        properties.put("countdown_server_synchronized", "true");
        explosionDefaults(properties);
        return Map.copyOf(properties);
    }

    private static Map<String, String> hiddenRadius(float defaultRadius, float hiddenMax) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("explosion_radius_default", Float.toString(defaultRadius));
        properties.put("explosion_radius_hidden_min", "0.0");
        properties.put("explosion_radius_hidden_max", Float.toString(hiddenMax));
        properties.put("server_hidden_explosion_power", "true");
        return properties;
    }

    private static void explosionDefaults(Map<String, String> properties) {
        properties.put("source_key", "minecraft:explosion");
        properties.put("scales_with_difficulty", "true");
    }
}
