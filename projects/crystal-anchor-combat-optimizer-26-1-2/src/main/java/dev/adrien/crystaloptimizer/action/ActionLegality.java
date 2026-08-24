package dev.adrien.crystaloptimizer.action;

import java.util.Objects;

public record ActionLegality(boolean legal, String reason) {
    public ActionLegality {
        Objects.requireNonNull(reason, "reason");
    }

    public static ActionLegality allowed() {
        return new ActionLegality(true, "legal");
    }

    public static ActionLegality denied(String reason) {
        return new ActionLegality(false, reason);
    }
}
