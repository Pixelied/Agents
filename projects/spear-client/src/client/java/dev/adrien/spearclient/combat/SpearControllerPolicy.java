package dev.adrien.spearclient.combat;

public final class SpearControllerPolicy {
    private SpearControllerPolicy() {}

    public static Action choose(
        boolean oneTapEnabled,
        boolean lungeEnabled,
        boolean reachEnabled,
        boolean oneTapAvailable,
        boolean lungeAvailable
    ) {
        if (oneTapEnabled && oneTapAvailable) {
            return Action.ONE_TAP;
        }
        if (lungeEnabled && lungeAvailable) {
            return Action.LUNGE;
        }
        if (reachEnabled) {
            return Action.REACH;
        }
        return Action.VANILLA;
    }

    public enum Action {
        ONE_TAP,
        LUNGE,
        REACH,
        VANILLA
    }
}
