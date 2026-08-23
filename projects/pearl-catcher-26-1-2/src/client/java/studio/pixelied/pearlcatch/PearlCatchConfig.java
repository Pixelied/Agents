package studio.pixelied.pearlcatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runtime configuration stored at config/pearlcatcher.json. */
public final class PearlCatchConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pearlcatcher.json");

    public boolean enabled = true;
    public boolean debugOverlay = true;
    public RotationMode rotationMode = RotationMode.SILENT_PACKET;
    public ItemSwitchMode itemSwitchMode = ItemSwitchMode.FAST;
    public boolean autoRestoreSlot = true;

    /** Search horizon only; higher is not a quality setting. */
    public int maxPredictionTicks = 40;
    /** Preferred forward range along the current crosshair ray where the catch should happen. */
    public double targetCatchDistance = 12.0;
    /** Maximum perpendicular miss from the current crosshair ray accepted by the planner. */
    public double maxCrosshairDistance = 1.5;


    public double pitchSweepStart = -90.0;
    public double pitchSweepEnd = 90.0;
    public double pitchSweepStep = 5.0;
    public int maxTicksPerPitch = 140;
    public int debugBetweenShotsTicks = 6;
    public boolean debugVisualization = true;
    public boolean debugExport = true;
    public boolean debugChat = true;
    public int debugParticleStride = 1;
    public int debugTrailLimit = 220;

    public static PearlCatchConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                PearlCatchConfig loaded = GSON.fromJson(reader, PearlCatchConfig.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            } catch (Exception ignored) {
                // Rewrite clean defaults below rather than letting a malformed config brick startup.
            }
        }
        PearlCatchConfig defaults = new PearlCatchConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }

    public void resetToDefaults() {
        PearlCatchConfig d = new PearlCatchConfig();
        enabled = d.enabled;
        debugOverlay = d.debugOverlay;
        rotationMode = d.rotationMode;
        itemSwitchMode = d.itemSwitchMode;
        autoRestoreSlot = d.autoRestoreSlot;
        maxPredictionTicks = d.maxPredictionTicks;
        targetCatchDistance = d.targetCatchDistance;
        maxCrosshairDistance = d.maxCrosshairDistance;
        pitchSweepStart = d.pitchSweepStart;
        pitchSweepEnd = d.pitchSweepEnd;
        pitchSweepStep = d.pitchSweepStep;
        maxTicksPerPitch = d.maxTicksPerPitch;
        debugBetweenShotsTicks = d.debugBetweenShotsTicks;
        debugVisualization = d.debugVisualization;
        debugExport = d.debugExport;
        debugChat = d.debugChat;
        debugParticleStride = d.debugParticleStride;
        debugTrailLimit = d.debugTrailLimit;
        save();
    }

    /** Internal hard work/range cap. This is deliberately not the user-facing target distance. */
    public double solverSearchDistance() {
        return Math.min(128.0, Math.max(24.0, targetCatchDistance + 24.0));
    }

    public void sanitize() {
        if (rotationMode == null) rotationMode = RotationMode.SILENT_PACKET;
        if (itemSwitchMode == null) itemSwitchMode = ItemSwitchMode.FAST;
        maxPredictionTicks = clamp(maxPredictionTicks, 2, 120);
        targetCatchDistance = clamp(targetCatchDistance, 1.0, 64.0);
        maxCrosshairDistance = clamp(maxCrosshairDistance, 0.1, 8.0);
        pitchSweepStart = clamp(pitchSweepStart, -90.0, 90.0);
        pitchSweepEnd = clamp(pitchSweepEnd, -90.0, 90.0);
        pitchSweepStep = clamp(pitchSweepStep, -45.0, 45.0);
        if (Math.abs(pitchSweepStep) < 0.25) {
            pitchSweepStep = pitchSweepEnd >= pitchSweepStart ? 5.0 : -5.0;
        }
        if (pitchSweepEnd > pitchSweepStart && pitchSweepStep < 0.0) pitchSweepStep = -pitchSweepStep;
        if (pitchSweepEnd < pitchSweepStart && pitchSweepStep > 0.0) pitchSweepStep = -pitchSweepStep;
        maxTicksPerPitch = clamp(maxTicksPerPitch, 20, 400);
        debugBetweenShotsTicks = clamp(debugBetweenShotsTicks, 0, 80);
        debugParticleStride = clamp(debugParticleStride, 1, 8);
        debugTrailLimit = clamp(debugTrailLimit, 20, 600);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }


    public enum ItemSwitchMode {
        FAST,
        LEGIT;

        public ItemSwitchMode next() {
            ItemSwitchMode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public String label() {
            return switch (this) {
                case FAST -> "Fast";
                case LEGIT -> "Legit";
            };
        }
    }

    public enum RotationMode {
        SILENT_PACKET,
        VISIBLE_CAMERA,
        CURRENT_CAMERA;

        public RotationMode next() {
            RotationMode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public String label() {
            return switch (this) {
                case SILENT_PACKET -> "Silent";
                case VISIBLE_CAMERA -> "Visible";
                case CURRENT_CAMERA -> "Current";
            };
        }
    }

}
