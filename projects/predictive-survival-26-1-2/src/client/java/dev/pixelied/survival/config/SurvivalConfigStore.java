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
    private static final Pattern MODE = Pattern.compile("\\\"safetyMode\\\"\\s*:\\s*\\\"([A-Z_]+)\\\"");
    private static final Pattern RESTORE = boolPattern("restoreHandState");
    private static final Pattern MOVEMENT = boolPattern("automaticMovement");
    private static final Pattern CLUTCHES = boolPattern("blockPlacementAndClutches");
    private static final Pattern DEBUG = boolPattern("debugEnabled");

    private final Path path;

    public SurvivalConfigStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public SurvivalConfig load() throws IOException {
        if (!Files.exists(path)) return SurvivalConfig.defaults();
        String json = Files.readString(path, StandardCharsets.UTF_8);
        try {
            Matcher modeMatcher = MODE.matcher(json);
            if (!modeMatcher.find()) return SurvivalConfig.defaults();
            Boolean restore = findBoolean(RESTORE, json);
            Boolean movement = findBoolean(MOVEMENT, json);
            Boolean clutches = findBoolean(CLUTCHES, json);
            Boolean debug = findBoolean(DEBUG, json);
            if (restore == null || movement == null || clutches == null || debug == null) {
                return SurvivalConfig.defaults();
            }
            SafetyMode mode = SafetyMode.valueOf(modeMatcher.group(1));
            return new SurvivalConfig(mode, restore, movement, clutches, debug);
        } catch (IllegalArgumentException ignored) {
            return SurvivalConfig.defaults();
        }
    }

    public void save(SurvivalConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        String json = "{\n"
            + "  \"safetyMode\": \"" + config.safetyMode().name() + "\",\n"
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

    private static Boolean findBoolean(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }
}
