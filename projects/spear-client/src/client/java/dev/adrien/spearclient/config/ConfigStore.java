package dev.adrien.spearclient.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigStore {
    private static final Logger LOGGER = Logger.getLogger(ConfigStore.class.getName());
    private static final AtomicBoolean WARNED_LOAD_FAILURE = new AtomicBoolean();

    private final Path path;

    public ConfigStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public Path path() {
        return path;
    }

    public SpearConfig load() {
        if (!Files.exists(path)) {
            return SpearConfig.defaults();
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            String oneTap = object(json, "oneTap");
            String lunge = object(json, "lungeBoost");
            String reach = object(json, "infiniteReach");

            return new SpearConfig(
                new SpearConfig.OneTapConfig(
                    bool(oneTap, "enabled"),
                    enumValue(oneTap, "mode", SpearConfig.OneTapMode.class)
                ),
                new SpearConfig.LungeConfig(
                    bool(lunge, "enabled"),
                    enumValue(lunge, "mode", SpearConfig.LungeMode.class)
                ),
                new SpearConfig.ReachConfig(
                    bool(reach, "enabled"),
                    enumValue(reach, "mode", SpearConfig.ReachMode.class),
                    bool(reach, "teamCheck")
                ),
                bool(json, "debug")
            ).sanitized();
        } catch (Exception failure) {
            if (WARNED_LOAD_FAILURE.compareAndSet(false, true)) {
                LOGGER.log(Level.WARNING, "Failed to load spear client config; using safe defaults", failure);
            }
            return SpearConfig.defaults();
        }
    }

    public void save(SpearConfig config) throws IOException {
        SpearConfig value = Objects.requireNonNullElse(config, SpearConfig.defaults()).sanitized();
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, toJson(value), StandardCharsets.UTF_8);
        try {
            Files.move(
                temp,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String toJson(SpearConfig config) {
        return "{\n"
            + "  \"oneTap\": {\"enabled\": " + config.oneTap().enabled()
            + ", \"mode\": \"" + config.oneTap().mode().name() + "\"},\n"
            + "  \"lungeBoost\": {\"enabled\": " + config.lungeBoost().enabled()
            + ", \"mode\": \"" + config.lungeBoost().mode().name() + "\"},\n"
            + "  \"infiniteReach\": {\"enabled\": " + config.infiniteReach().enabled()
            + ", \"mode\": \"" + config.infiniteReach().mode().name() + "\""
            + ", \"teamCheck\": " + config.infiniteReach().teamCheck() + "},\n"
            + "  \"debug\": " + config.debug() + "\n"
            + "}\n";
    }

    private static String object(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\{([^{}]*)\\}",
            Pattern.DOTALL
        ).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing object: " + key);
        }
        return matcher.group(1);
    }

    private static boolean bool(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false)",
            Pattern.CASE_INSENSITIVE
        ).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing boolean: " + key);
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static <E extends Enum<E>> E enumValue(String json, String key, Class<E> type) {
        Matcher matcher = Pattern.compile(
            "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([A-Za-z0-9_]+)\\\""
        ).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing enum: " + key);
        }
        return Enum.valueOf(type, matcher.group(1));
    }
}
