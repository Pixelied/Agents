package dev.pixelied.survival.config;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/** Persists a complete immutable config before atomically handing it to the live engine. */
public final class LiveConfigController {
    private final SurvivalConfigStore store;
    private final Consumer<SurvivalConfig> liveApplier;

    public LiveConfigController(SurvivalConfigStore store, Consumer<SurvivalConfig> liveApplier) {
        this.store = Objects.requireNonNull(store, "store");
        this.liveApplier = Objects.requireNonNull(liveApplier, "liveApplier");
    }

    public void apply(SurvivalConfig config) throws IOException {
        SurvivalConfig immutable = Objects.requireNonNull(config, "config");
        store.save(immutable);
        liveApplier.accept(immutable);
    }
}
