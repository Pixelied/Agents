package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.state.StrategicResult;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.StrategicComputation;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Single-worker latest-wins strategic planner. Never blocks the client thread. */
public final class ClientStrategicPlannerService implements AutoCloseable {
    private final StrategicComputation computation;
    private final ExecutorService worker;
    private final AtomicLong latestToken = new AtomicLong();
    private final AtomicReference<StrategicResult> latest = new AtomicReference<>();

    public ClientStrategicPlannerService(StrategicComputation computation) {
        this.computation = Objects.requireNonNull(computation, "computation");
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "crystaloptimizer-strategic");
            thread.setDaemon(true);
            return thread;
        };
        this.worker = Executors.newSingleThreadExecutor(threadFactory);
    }

    public long submit(StrategicSnapshot snapshot, OptimizerConfig config) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(config, "config");
        long token = latestToken.incrementAndGet();
        worker.execute(() -> {
            StrategicResult result = computation.compute(snapshot, config);
            if (result != null && latestToken.get() == token) {
                latest.set(result);
            }
        });
        return token;
    }

    public Optional<StrategicResult> pollLatest() {
        return Optional.ofNullable(latest.getAndSet(null));
    }

    @Override
    public void close() {
        latestToken.incrementAndGet();
        latest.set(null);
        worker.shutdownNow();
    }
}
