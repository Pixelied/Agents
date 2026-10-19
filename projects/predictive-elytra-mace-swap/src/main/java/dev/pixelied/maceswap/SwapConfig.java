package dev.pixelied.maceswap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** All values are conservative and normalized when loading untrusted disk data. */
public final class SwapConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("predictivemaceswap");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("predictive-elytra-mace-swap.json");
    public boolean enabled = true;
    public boolean automaticRecovery = true;
    public boolean requireAim = true;
    public boolean debugOverlay = false;
    public int predictionTicks = 18;
    public int extraLeadTicks = 1;
    public int opportunityTicks = 5;
    public int latencyFloorMs = 50;
    public int rangeSafetyPercent = 12;
    public int recoveryRetries = 8;
    public int cooldownTicks = 14;
    public int searchRadius = 16;

    public static SwapConfig load() {
        if (!Files.exists(FILE)) return new SwapConfig();
        try {
            SwapConfig saved = GSON.fromJson(Files.readString(FILE), SwapConfig.class);
            if (saved == null) return new SwapConfig();
            saved.normalize();
            return saved;
        } catch (RuntimeException | IOException ex) {
            LOGGER.warn("Configuration could not be loaded; using safe defaults", ex);
            return new SwapConfig();
        }
    }

    public SwapConfig copy() {
        return GSON.fromJson(GSON.toJson(this), SwapConfig.class);
    }

    public void normalize() {
        predictionTicks = clamp(predictionTicks, 8, 32);
        extraLeadTicks = clamp(extraLeadTicks, 0, 8);
        opportunityTicks = clamp(opportunityTicks, 2, 10);
        latencyFloorMs = clamp(latencyFloorMs, 0, 500);
        rangeSafetyPercent = clamp(rangeSafetyPercent, 0, 45);
        recoveryRetries = clamp(recoveryRetries, 1, 20);
        cooldownTicks = clamp(cooldownTicks, 5, 60);
        searchRadius = clamp(searchRadius, 6, 24);
    }

    private static int clamp(int value, int lo, int hi) { return Math.max(lo, Math.min(hi, value)); }

    public void save() {
        normalize();
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            try { Files.move(tmp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException unsupported) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) {
            LOGGER.error("Could not save config {}", FILE, ex);
        }
    }
}
