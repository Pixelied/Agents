package dev.adrien.naturalaim;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

public final class NaturalAimConfig {
    private static final String FILE_NAME = "naturalaim.properties";

    public static final double MIN_STRENGTH = 0.0;
    public static final double MAX_STRENGTH = 1.0;
    public static final double MIN_FOV = 1.0;
    public static final double MAX_FOV = 360.0;
    public static final double MIN_RANGE = 1.0;
    public static final double MAX_RANGE = 12.0;

    public enum Preset {
        NATURAL("Natural", 105.0, 75.0, 950.0, 1400.0, 18.0, 0.72),
        BALANCED("Balanced", 160.0, 115.0, 1500.0, 2200.0, 28.0, 0.88),
        STRONG("Strong", 240.0, 170.0, 2400.0, 3400.0, 42.0, 1.0);

        private final String displayName;
        private final double maxYawSpeed;
        private final double maxPitchSpeed;
        private final double acceleration;
        private final double deceleration;
        private final double gain;
        private final double strengthScale;

        Preset(String displayName, double maxYawSpeed, double maxPitchSpeed, double acceleration,
               double deceleration, double gain, double strengthScale) {
            this.displayName = displayName;
            this.maxYawSpeed = maxYawSpeed;
            this.maxPitchSpeed = maxPitchSpeed;
            this.acceleration = acceleration;
            this.deceleration = deceleration;
            this.gain = gain;
            this.strengthScale = strengthScale;
        }

        public String displayName() { return displayName; }
        public double maxYawSpeed() { return maxYawSpeed; }
        public double maxPitchSpeed() { return maxPitchSpeed; }
        public double acceleration() { return acceleration; }
        public double deceleration() { return deceleration; }
        public double gain() { return gain; }
        public double strengthScale() { return strengthScale; }

        public Preset next() {
            Preset[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private boolean enabled = true;
    private Preset preset = Preset.NATURAL;
    private double strength = 0.50;
    private double assistFov = 30.0;
    private double range = 4.5;
    private boolean verticalAssist = true;
    private boolean requireAttack = true;
    private boolean weaponsOnly = true;
    private boolean targetPlayers = true;
    private boolean targetMobs = false;
    private boolean visibleOnly = true;
    private boolean ignoreInvisible = true;
    private boolean pauseActions = true;

    public static NaturalAimConfig load() {
        NaturalAimConfig config = new NaturalAimConfig();
        Path path = path();
        if (!Files.isRegularFile(path)) {
            config.save();
            return config;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            config.enabled = bool(properties, "enabled", config.enabled);
            config.preset = preset(properties.getProperty("preset"), config.preset);
            config.strength = number(properties, "strength", config.strength, MIN_STRENGTH, MAX_STRENGTH);
            config.assistFov = number(properties, "assistFov", config.assistFov, MIN_FOV, MAX_FOV);
            config.range = number(properties, "range", config.range, MIN_RANGE, MAX_RANGE);
            config.verticalAssist = bool(properties, "verticalAssist", config.verticalAssist);
            config.requireAttack = bool(properties, "requireAttack", config.requireAttack);
            config.weaponsOnly = bool(properties, "weaponsOnly", config.weaponsOnly);
            config.targetPlayers = bool(properties, "targetPlayers", config.targetPlayers);
            config.targetMobs = bool(properties, "targetMobs", config.targetMobs);
            config.visibleOnly = bool(properties, "visibleOnly", config.visibleOnly);
            config.ignoreInvisible = bool(properties, "ignoreInvisible", config.ignoreInvisible);
            config.pauseActions = bool(properties, "pauseActions", config.pauseActions);
        } catch (IOException | RuntimeException exception) {
            NaturalAimClient.LOGGER.warn("Failed to load {}; using safe defaults", path, exception);
        }
        return config;
    }

    public synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("preset", preset.name());
        properties.setProperty("strength", format(strength));
        properties.setProperty("assistFov", format(assistFov));
        properties.setProperty("range", format(range));
        properties.setProperty("verticalAssist", Boolean.toString(verticalAssist));
        properties.setProperty("requireAttack", Boolean.toString(requireAttack));
        properties.setProperty("weaponsOnly", Boolean.toString(weaponsOnly));
        properties.setProperty("targetPlayers", Boolean.toString(targetPlayers));
        properties.setProperty("targetMobs", Boolean.toString(targetMobs));
        properties.setProperty("visibleOnly", Boolean.toString(visibleOnly));
        properties.setProperty("ignoreInvisible", Boolean.toString(ignoreInvisible));
        properties.setProperty("pauseActions", Boolean.toString(pauseActions));

        Path path = path();
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "Natural Aim Assist configuration");
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            NaturalAimClient.LOGGER.warn("Failed to save {}", path, exception);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    public void resetDefaults() {
        enabled = true;
        preset = Preset.NATURAL;
        strength = 0.50;
        assistFov = 30.0;
        range = 4.5;
        verticalAssist = true;
        requireAttack = true;
        weaponsOnly = true;
        targetPlayers = true;
        targetMobs = false;
        visibleOnly = true;
        ignoreInvisible = true;
        pauseActions = true;
        save();
    }

    public boolean enabled() { return enabled; }
    public Preset preset() { return preset; }
    public double strength() { return strength; }
    public double assistFov() { return assistFov; }
    public double range() { return range; }
    public boolean verticalAssist() { return verticalAssist; }
    public boolean requireAttack() { return requireAttack; }
    public boolean weaponsOnly() { return weaponsOnly; }
    public boolean targetPlayers() { return targetPlayers; }
    public boolean targetMobs() { return targetMobs; }
    public boolean visibleOnly() { return visibleOnly; }
    public boolean ignoreInvisible() { return ignoreInvisible; }
    public boolean pauseActions() { return pauseActions; }

    public void toggleEnabled() { enabled = !enabled; save(); }
    public void cyclePreset() { preset = preset.next(); save(); }

    public void setStrength(double value) {
        strength = AimMath.clamp(value, MIN_STRENGTH, MAX_STRENGTH);
        save();
    }

    public void setAssistFov(double value) {
        assistFov = AimMath.clamp(value, MIN_FOV, MAX_FOV);
        save();
    }

    public void setRange(double value) {
        range = AimMath.clamp(value, MIN_RANGE, MAX_RANGE);
        save();
    }

    public void toggleVerticalAssist() { verticalAssist = !verticalAssist; save(); }
    public void toggleRequireAttack() { requireAttack = !requireAttack; save(); }
    public void toggleWeaponsOnly() { weaponsOnly = !weaponsOnly; save(); }
    public void toggleTargetPlayers() { targetPlayers = !targetPlayers; save(); }
    public void toggleTargetMobs() { targetMobs = !targetMobs; save(); }
    public void toggleVisibleOnly() { visibleOnly = !visibleOnly; save(); }
    public void toggleIgnoreInvisible() { ignoreInvisible = !ignoreInvisible; save(); }
    public void togglePauseActions() { pauseActions = !pauseActions; save(); }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static double number(Properties properties, String key, double fallback, double min, double max) {
        String raw = properties.getProperty(key);
        if (raw == null) return fallback;
        try {
            return AimMath.clamp(Double.parseDouble(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Preset preset(String raw, Preset fallback) {
        if (raw == null) return fallback;
        try {
            return Preset.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
