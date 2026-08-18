package dev.pixelied.survival.validation;

import java.util.Objects;

public record ValidationResult(
    String id,
    float predictedHealth,
    float actualHealth,
    ValidationStatus status,
    float tolerance
) {
    public ValidationResult {
        id = Objects.requireNonNull(id, "id");
        status = Objects.requireNonNull(status, "status");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (!Float.isFinite(predictedHealth) || !Float.isFinite(actualHealth)) {
            throw new IllegalArgumentException("health values must be finite");
        }
        if (!Float.isFinite(tolerance) || tolerance < 0f) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative");
        }
    }

    public boolean passes() {
        return Math.abs(predictedHealth - actualHealth) <= tolerance;
    }
}
