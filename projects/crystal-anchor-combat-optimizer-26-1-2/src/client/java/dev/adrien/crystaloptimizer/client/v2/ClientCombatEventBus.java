package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ClientCombatEventBus {
    private static final ClientCombatEventBus INSTANCE = new ClientCombatEventBus();

    private final CopyOnWriteArrayList<Consumer<CombatEvent>> listeners =
        new CopyOnWriteArrayList<>();

    public static ClientCombatEventBus instance() {
        return INSTANCE;
    }

    public Runnable subscribe(Consumer<CombatEvent> listener) {
        Consumer<CombatEvent> checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        return () -> listeners.remove(checked);
    }

    public void publish(CombatEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<CombatEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
