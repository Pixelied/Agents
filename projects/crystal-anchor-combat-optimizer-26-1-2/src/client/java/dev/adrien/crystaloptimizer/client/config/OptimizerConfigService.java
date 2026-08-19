package dev.adrien.crystaloptimizer.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

public final class OptimizerConfigService {
    private static final String FILE_NAME = "crystaloptimizer.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final AtomicReference<OptimizerConfig> current;
    private final AtomicLong revision = new AtomicLong();
    private final CopyOnWriteArrayList<Consumer<OptimizerConfig>> listeners = new CopyOnWriteArrayList<>();
    private final Path configPath;

    private OptimizerConfigService(OptimizerConfig initial, Path configPath) {
        current = new AtomicReference<>(Objects.requireNonNull(initial, "initial").validated());
        this.configPath = configPath;
    }

    public static OptimizerConfigService instance() {
        return Holder.INSTANCE;
    }

    public static OptimizerConfigService inMemory(OptimizerConfig initial) {
        return new OptimizerConfigService(initial, null);
    }

    public static OptimizerConfigService forDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        try {
            Files.createDirectories(directory);
            Path path = directory.resolve(FILE_NAME);
            if (!Files.exists(path)) {
                OptimizerConfigService service = new OptimizerConfigService(OptimizerConfig.defaults(), path);
                service.persist(service.current());
                return service;
            }

            try {
                String json = Files.readString(path);
                OptimizerConfig loaded = GSON.fromJson(json, OptimizerConfig.class);
                if (loaded == null) {
                    throw new IllegalArgumentException("config JSON resolved to null");
                }
                return new OptimizerConfigService(loaded.validated(), path);
            } catch (RuntimeException | IOException invalid) {
                Path quarantine = directory.resolve(FILE_NAME + ".invalid");
                Files.move(path, quarantine, StandardCopyOption.REPLACE_EXISTING);
                OptimizerConfigService service = new OptimizerConfigService(OptimizerConfig.defaults(), path);
                service.persist(service.current());
                return service;
            }
        } catch (IOException io) {
            throw new IllegalStateException("Unable to initialize Crystal Optimizer config", io);
        }
    }

    public OptimizerConfig current() {
        return current.get();
    }

    public long revision() {
        return revision.get();
    }

    public void apply(OptimizerConfig next) {
        OptimizerConfig validated = Objects.requireNonNull(next, "next").validated();
        persist(validated);
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

    private void persist(OptimizerConfig config) {
        if (configPath == null) {
            return;
        }
        Path temp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(config));
            try {
                Files.move(
                    temp,
                    configPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException io) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("Unable to save Crystal Optimizer config", io);
        }
    }

    private static final class Holder {
        private static final OptimizerConfigService INSTANCE = forDirectory(
            FabricLoader.getInstance().getConfigDir()
        );
    }
}
