package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SurvivalConfigStore {
    private static final int SCHEMA_VERSION = 3;
    private static final Pattern MODE = enumPattern("safetyMode");
    private static final Pattern PROFILE = enumPattern("rescueProfile");
    private static final Pattern TOTEM_HAND_PRIORITY = enumPattern("totemHandPriority");
    private static final Pattern DEATH_PROTECTION = boolPattern("deathProtection");
    private static final Pattern SHIELDS = boolPattern("shields");
    private static final Pattern CONSUMABLES = boolPattern("consumables");
    private static final Pattern EQUIPMENT = boolPattern("equipment");
    private static final Pattern INVENTORY_ROUTING = boolPattern("inventoryRouting");
    private static final Pattern MAIN_HAND_TAKEOVER = boolPattern("mainHandTakeover");
    private static final Pattern PROACTIVE_DUAL_PROTECTION = boolPattern("proactiveDualProtection");
    private static final Pattern RESTORE = boolPattern("restoreHandState");
    private static final Pattern MOVEMENT = boolPattern("automaticMovement");
    private static final Pattern CLUTCHES = boolPattern("blockPlacementAndClutches");
    private static final Pattern DEBUG = boolPattern("debugEnabled");

    private final Path path;

    public SurvivalConfigStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public SurvivalConfig load() throws IOException {
        SurvivalConfig defaults = SurvivalConfig.defaults();
        if (!Files.exists(path)) return defaults;
        String json = Files.readString(path, StandardCharsets.UTF_8);

        SafetyMode mode = findEnum(MODE, json, SafetyMode.class, defaults.safetyMode());
        RescueProfile profile = findEnum(PROFILE, json, RescueProfile.class, defaults.rescueProfile());
        TotemHandPriority totemHandPriority = findEnum(
            TOTEM_HAND_PRIORITY,
            json,
            TotemHandPriority.class,
            defaults.totemHandPriority()
        );
        RescuePolicy defaultCustom = defaults.customPolicy();
        RescuePolicy custom = new RescuePolicy(
            findBoolean(DEATH_PROTECTION, json, defaultCustom.deathProtection()),
            findBoolean(SHIELDS, json, defaultCustom.shields()),
            findBoolean(CONSUMABLES, json, defaultCustom.consumables()),
            findBoolean(EQUIPMENT, json, defaultCustom.equipment()),
            findBoolean(INVENTORY_ROUTING, json, defaultCustom.inventoryRouting()),
            findBoolean(MAIN_HAND_TAKEOVER, json, defaultCustom.mainHandTakeover()),
            findBoolean(PROACTIVE_DUAL_PROTECTION, json, defaultCustom.proactiveDualProtection())
        );
        boolean restore = findBoolean(RESTORE, json, defaults.restoreHandState());
        boolean movement = findBoolean(MOVEMENT, json, defaults.automaticMovement());
        boolean clutches = findBoolean(CLUTCHES, json, defaults.blockPlacementAndClutches());
        boolean debug = findBoolean(DEBUG, json, defaults.debugEnabled());
        return new SurvivalConfig(
            mode,
            profile,
            custom,
            totemHandPriority,
            restore,
            movement,
            clutches,
            debug
        );
    }

    public void save(SurvivalConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        RescuePolicy custom = config.customPolicy();
        String json = "{\n"
            + "  \"schemaVersion\": " + SCHEMA_VERSION + ",\n"
            + "  \"safetyMode\": \"" + config.safetyMode().name() + "\",\n"
            + "  \"rescueProfile\": \"" + config.rescueProfile().name() + "\",\n"
            + "  \"totemHandPriority\": \"" + config.totemHandPriority().name() + "\",\n"
            + "  \"deathProtection\": " + custom.deathProtection() + ",\n"
            + "  \"shields\": " + custom.shields() + ",\n"
            + "  \"consumables\": " + custom.consumables() + ",\n"
            + "  \"equipment\": " + custom.equipment() + ",\n"
            + "  \"inventoryRouting\": " + custom.inventoryRouting() + ",\n"
            + "  \"mainHandTakeover\": " + custom.mainHandTakeover() + ",\n"
            + "  \"proactiveDualProtection\": " + custom.proactiveDualProtection() + ",\n"
            + "  \"restoreHandState\": " + config.restoreHandState() + ",\n"
            + "  \"automaticMovement\": " + config.automaticMovement() + ",\n"
            + "  \"blockPlacementAndClutches\": " + config.blockPlacementAndClutches() + ",\n"
            + "  \"debugEnabled\": " + config.debugEnabled() + "\n"
            + "}\n";
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    public Path path() {
        return path;
    }

    private static Pattern boolPattern(String key) {
        return Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false)");
    }

    private static Pattern enumPattern(String key) {
        return Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([A-Z_]+)\\\"");
    }

    private static boolean findBoolean(Pattern pattern, String json, boolean fallback) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private static <E extends Enum<E>> E findEnum(
        Pattern pattern,
        String json,
        Class<E> enumType,
        E fallback
    ) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return fallback;
        try {
            return Enum.valueOf(enumType, matcher.group(1));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
