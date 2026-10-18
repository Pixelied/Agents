package gg.vape.fabric.input;

import java.util.concurrent.CopyOnWriteArrayList;

public final class FabricInputBridge {
    public interface Listener {
        default boolean onKey(int key, int scancode, int action, int modifiers) { return false; }
        default boolean onCharacter(int codepoint, int modifiers) { return false; }
        default boolean onMouseButton(int button, int action, int modifiers) { return false; }
        default void onMouseMove(double x, double y) { }
        default boolean onScroll(double xOffset, double yOffset) { return false; }
        default void onFocusLost() { }
    }

    private static final FabricInputState STATE = new FabricInputState();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private FabricInputBridge() { }
    public static FabricInputState state() { return STATE; }
    public static void addListener(Listener listener) { if (listener != null) LISTENERS.addIfAbsent(listener); }
    public static void removeListener(Listener listener) { LISTENERS.remove(listener); }

    public static boolean onKey(int key, int scancode, int action, int modifiers) {
        STATE.onKey(key, action, modifiers);
        boolean consumed = false;
        for (Listener listener : LISTENERS) consumed |= listener.onKey(key, scancode, action, modifiers);
        return consumed;
    }

    public static boolean onCharacter(int codepoint, int modifiers) {
        boolean consumed = false;
        for (Listener listener : LISTENERS) consumed |= listener.onCharacter(codepoint, modifiers);
        return consumed;
    }

    public static boolean onMouseButton(int button, int action, int modifiers) {
        STATE.onMouseButton(button, action, modifiers);
        boolean consumed = false;
        for (Listener listener : LISTENERS) consumed |= listener.onMouseButton(button, action, modifiers);
        return consumed;
    }

    public static void onMouseMove(double x, double y) {
        STATE.onMouseMove(x, y);
        for (Listener listener : LISTENERS) listener.onMouseMove(x, y);
    }

    public static boolean onScroll(double xOffset, double yOffset) {
        STATE.onScroll(xOffset, yOffset);
        boolean consumed = false;
        for (Listener listener : LISTENERS) consumed |= listener.onScroll(xOffset, yOffset);
        return consumed;
    }

    public static void onFocusLost() {
        STATE.clear();
        for (Listener listener : LISTENERS) listener.onFocusLost();
    }
}
