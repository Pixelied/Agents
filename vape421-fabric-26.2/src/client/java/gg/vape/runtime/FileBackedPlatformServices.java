package gg.vape.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

public class FileBackedPlatformServices implements PlatformServices.Provider {
    private final Path configDirectory;

    public FileBackedPlatformServices(Path configDirectory) {
        this.configDirectory = configDirectory.toAbsolutePath().normalize();
    }

    protected Path settingsFile() {
        return configDirectory.resolve("settings.json");
    }

    @Override
    public String loadSettings(String key) {
        if (!"all".equals(key) || !Files.isRegularFile(settingsFile())) return null;
        try {
            String json = Files.readString(settingsFile(), StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log("Unable to read local settings: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void saveSettings(String value) {
        if (value == null) return;
        try {
            Files.createDirectories(configDirectory);
            Path target = settingsFile();
            Path temp = configDirectory.resolve("settings.json.tmp");
            Files.writeString(temp, value, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log("Unable to save local settings: " + e.getMessage());
        }
    }
}
