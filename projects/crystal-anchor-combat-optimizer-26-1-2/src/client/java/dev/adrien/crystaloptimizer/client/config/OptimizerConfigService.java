package dev.adrien.crystaloptimizer.client.config;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class OptimizerConfigService {
    private static final OptimizerConfigService INSTANCE = inMemory(OptimizerConfig.defaults());

    private final AtomicReference<OptimizerConfig> current;
    private final AtomicLong revision = new AtomicLong();
    private final CopyOnWriteArrayList<Consumer<OptimizerConfig>> listeners = new CopyOnWriteArrayList<>();

    private OptimizerConfigService(OptimizerConfig initial) {
        current = new AtomicReference<>(Objects.requireNonNull(initial, "initial").validated());
    }

    public static OptimizerConfigService instance() {
        return INSTANCE;
    }

    public static OptimizerConfigService inMemory(OptimizerConfig initial) {
        return new OptimizerConfigService(initial);
    }

    public OptimizerConfig current() {
        return current.get();
    }

    public long revision() {
        return revision.get();
    }

    public void apply(OptimizerConfig next) {
        OptimizerConfig validated = Objects.requireNonNull(next, "next").validated();
        current.set(validated);
        revision.incrementAndGet();
        for (Consumer<OptimizerConfig> listener : listeners) {
            listener.accept(validated);
        }
    }

    public Runnable addListener(Consumer<OptimizerConfig> listener) {
        Consumer<OptimizerConfig> checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        return () -> listeners.remove(checked);
    }
}
