package dev.pixelied.survival.damage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class DamageTrace {
    private final Map<DamageStage, Float> before;
    private final Map<DamageStage, Float> after;

    private DamageTrace(Map<DamageStage, Float> before, Map<DamageStage, Float> after) {
        this.before = Collections.unmodifiableMap(new EnumMap<>(before));
        this.after = Collections.unmodifiableMap(new EnumMap<>(after));
    }

    public float before(DamageStage stage) {
        Float value = before.get(stage);
        if (value == null) throw new IllegalArgumentException("stage not recorded: " + stage);
        return value;
    }

    public float after(DamageStage stage) {
        Float value = after.get(stage);
        if (value == null) throw new IllegalArgumentException("stage not recorded: " + stage);
        return value;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final EnumMap<DamageStage, Float> before = new EnumMap<>(DamageStage.class);
        private final EnumMap<DamageStage, Float> after = new EnumMap<>(DamageStage.class);

        void record(DamageStage stage, float beforeValue, float afterValue) {
            before.put(stage, beforeValue);
            after.put(stage, afterValue);
        }

        DamageTrace build() {
            return new DamageTrace(before, after);
        }
    }
}
