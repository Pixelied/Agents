package dev.adrien.crystaloptimizer.v2.timing;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record TimingCorrelation(
    TimingTransition transition,
    long high,
    long low
) {
    public TimingCorrelation {
        Objects.requireNonNull(transition, "transition");
    }

    public static TimingCorrelation sequence(TimingTransition transition, int sequence) {
        return new TimingCorrelation(transition, 0L, Integer.toUnsignedLong(sequence));
    }

    public static TimingCorrelation entity(TimingTransition transition, int entityId) {
        return new TimingCorrelation(transition, 1L, Integer.toUnsignedLong(entityId));
    }

    public static TimingCorrelation place(
        TimingTransition transition,
        int sequence,
        BlockPos pos
    ) {
        Objects.requireNonNull(pos, "pos");
        return new TimingCorrelation(
            transition,
            Integer.toUnsignedLong(sequence),
            pos.asLong()
        );
    }

    public static TimingCorrelation player(TimingTransition transition, UUID id) {
        Objects.requireNonNull(id, "id");
        return new TimingCorrelation(
            transition,
            id.getMostSignificantBits(),
            id.getLeastSignificantBits()
        );
    }
}
