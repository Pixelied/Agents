package dev.adrien.spearclient.combat;

public final class SpearControllerPolicy {
    private SpearControllerPolicy() {}

    public static Action choose(
        boolean oneTapEnabled,
        boolean reachEnabled,
        boolean oneTapAvailable
    ) {
        if (oneTapEnabled && oneTapAvailable) {
            return Action.ONE_TAP;
        }
        if (reachEnabled) {
            return Action.REACH;
        }
        return Action.VANILLA;
    }

    public enum Action {
        ONE_TAP,
        REACH,
        VANILLA
    }
}
