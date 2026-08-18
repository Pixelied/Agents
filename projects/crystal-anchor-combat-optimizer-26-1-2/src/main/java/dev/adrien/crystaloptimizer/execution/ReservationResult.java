package dev.adrien.crystaloptimizer.execution;

import java.util.List;
import java.util.Optional;

public record ReservationResult(
    boolean granted,
    Optional<ReservationToken> token,
    List<ReservationToken> revokedTokens,
    String reason
) {
    public ReservationResult {
        token = token == null ? Optional.empty() : token;
        revokedTokens = List.copyOf(revokedTokens);
        reason = reason == null ? "" : reason;
        if (granted != token.isPresent()) {
            throw new IllegalArgumentException("granted result must carry exactly one token");
        }
    }

    public static ReservationResult granted(ReservationToken token, List<ReservationToken> revokedTokens) {
        return new ReservationResult(true, Optional.of(token), revokedTokens, "");
    }

    public static ReservationResult denied(String reason) {
        return new ReservationResult(false, Optional.empty(), List.of(), reason);
    }
}
