package dev.adrien.spearclient.config;

import java.util.Objects;

public record SpearConfig(
    OneTapConfig oneTap,
    LungeConfig lungeBoost,
    ReachConfig infiniteReach,
    boolean debug
) {
    public SpearConfig {
        oneTap = Objects.requireNonNullElse(oneTap, new OneTapConfig(false, OneTapMode.SMART));
        lungeBoost = Objects.requireNonNullElse(lungeBoost, new LungeConfig(false, LungeMode.SMART));
        infiniteReach = Objects.requireNonNullElse(
            infiniteReach,
            new ReachConfig(false, ReachMode.SMART, true)
        );
    }

    public static SpearConfig defaults() {
        return new SpearConfig(
            new OneTapConfig(false, OneTapMode.SMART),
            new LungeConfig(false, LungeMode.SMART),
            new ReachConfig(false, ReachMode.SMART, true),
            false
        );
    }

    public SpearConfig sanitized() {
        return new SpearConfig(oneTap, lungeBoost, infiniteReach, debug);
    }

    public enum OneTapMode { SMART }
    public enum LungeMode { SMART }
    public enum ReachMode { SMART }

    public record OneTapConfig(boolean enabled, OneTapMode mode) {
        public OneTapConfig {
            mode = Objects.requireNonNullElse(mode, OneTapMode.SMART);
        }
    }

    public record LungeConfig(boolean enabled, LungeMode mode) {
        public LungeConfig {
            mode = Objects.requireNonNullElse(mode, LungeMode.SMART);
        }
    }

    public record ReachConfig(boolean enabled, ReachMode mode, boolean teamCheck) {
        public ReachConfig {
            mode = Objects.requireNonNullElse(mode, ReachMode.SMART);
        }
    }
}
