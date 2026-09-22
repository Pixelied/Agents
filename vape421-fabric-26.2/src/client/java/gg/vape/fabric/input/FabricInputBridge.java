package gg.vape.fabric.input;

import java.util.concurrent.CopyOnWriteArrayList;

public final class FabricInputBridge {
    public interface Listener {
        default void onKey(int key, int scancode, int action, int modifiers) { }
        default void onMouseButton(int button, int action, int modifiers) { }
        default void onScroll(double xOffset, double yOffset) { }
        default void onFocusLost() { }
    }

    private static final FabricInputState STATE = new FabricInputState();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private FabricInputBridge() { }
    public static FabricInputState state() { return STATE; }
    public static void addListener(Listener listener) { if (listener != null) LISTENERS.addIfAbsent(listener); }
    public static void removeListener(Listener listener) { LISTENERS.remove(listener); }

    public static void onKey(int key, int scancode, int action, int modifiers) {
        STATE.onKey(key, action, modifiers);
        for (Listener listener : LISTENERS) listener.onKey(key, scancode, action, modifiers);
    }

    public static void onMouseButton(int button, int action, int modifiers) {
        STATE.onMouseButton(button, action, modifiers);
        for (Listener listener : LISTENERS) listener.onMouseButton(button, action, modifiers);
    }

    public static void onScroll(double xOffset, double yOffset) {
        STATE.onScroll(xOffset, yOffset);
        for (Listener listener : LISTENERS) listener.onScroll(xOffset, yOffset);
    }

    public static void onFocusLost() {
        STATE.clear();
        for (Listener listener : LISTENERS) listener.onFocusLost();
    }
}
