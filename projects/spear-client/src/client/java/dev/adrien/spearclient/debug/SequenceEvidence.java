package dev.adrien.spearclient.debug;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record SequenceEvidence(
    long sequenceId,
    String kind,
    String result,
    int targetId,
    int packets,
    Vec3 origin,
    double maxRequestedDelta,
    double predictedKnownForward,
    double predictedRawDamage,
    double predictedReach,
    int corrections
) {
    public SequenceEvidence {
        kind = Objects.requireNonNullElse(kind, "NONE");
        result = Objects.requireNonNullElse(result, "aborted");
        origin = Objects.requireNonNullElse(origin, Vec3.ZERO);
    }

    public String format() {
        return "sequence=" + sequenceId
            + " kind=" + kind
            + " result=" + result
            + " target=" + targetId
            + " packets=" + packets
            + " origin=" + vector(origin)
            + " maxRequestedDelta=" + number(maxRequestedDelta)
            + " predictedKnownForward=" + number(predictedKnownForward)
            + " predictedRawDamage=" + number(predictedRawDamage)
            + " predictedReach=" + number(predictedReach)
            + " corrections=" + corrections;
    }

    private static String vector(Vec3 value) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", value.x, value.y, value.z);
    }

    private static String number(double value) {
        return Double.isFinite(value)
            ? String.format(Locale.ROOT, "%.3f", value)
            : "n/a";
    }
}
