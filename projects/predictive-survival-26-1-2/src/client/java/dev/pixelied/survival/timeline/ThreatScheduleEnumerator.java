package dev.pixelied.survival.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ThreatScheduleEnumerator {
    private ThreatScheduleEnumerator() {
    }

    static Result enumerate(List<ThreatEvent> order, long previousTick, int maxSchedules) {
        Objects.requireNonNull(order, "order");
        if (maxSchedules <= 0) throw new IllegalArgumentException("maxSchedules must be positive");

        List<long[]> schedules = new ArrayList<>(Math.min(maxSchedules, 64));
        boolean exhaustive = enumerate(order, 0, previousTick, new long[order.size()], schedules, maxSchedules);
        return new Result(schedules, exhaustive);
    }

    private static boolean enumerate(
        List<ThreatEvent> order,
        int index,
        long minimumTick,
        long[] working,
        List<long[]> output,
        int maxSchedules
    ) {
        if (index == order.size()) {
            if (output.size() >= maxSchedules) return false;
            output.add(working.clone());
            return true;
        }

        ThreatEvent event = order.get(index);
        long lower = Math.max(event.impact().earliest(), minimumTick);
        long upper = event.impact().latest();
        if (upper < lower) return true;

        for (long tick = lower; ; tick++) {
            working[index] = tick;
            if (!enumerate(order, index + 1, tick, working, output, maxSchedules)) return false;
            if (tick == upper) break;
        }
        return true;
    }

    record Result(List<long[]> schedules, boolean exhaustive) {
        Result {
            schedules = List.copyOf(schedules);
        }
    }
}
