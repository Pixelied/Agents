package gg.vape.fabric.input;

import java.util.Arrays;

public final class FabricInputState {
    private static final int KEY_CAPACITY = 512;
    private static final int MOUSE_CAPACITY = 16;
    private final boolean[] keys = new boolean[KEY_CAPACITY];
    private final boolean[] mouseButtons = new boolean[MOUSE_CAPACITY];
    private volatile double scrollX;
    private volatile double scrollY;
    private volatile int modifiers;

    public void onKey(int glfwKey, int action, int modifierMask) {
        if (glfwKey >= 0 && glfwKey < keys.length) keys[glfwKey] = action != 0;
        modifiers = modifierMask;
    }

    public void onMouseButton(int glfwButton, int action, int modifierMask) {
        if (glfwButton >= 0 && glfwButton < mouseButtons.length) mouseButtons[glfwButton] = action != 0;
        modifiers = modifierMask;
    }

    public void onScroll(double xOffset, double yOffset) { scrollX += xOffset; scrollY += yOffset; }
    public boolean isKeyDown(int glfwKey) { return glfwKey >= 0 && glfwKey < keys.length && keys[glfwKey]; }
    public boolean isMouseButtonDown(int glfwButton) { return glfwButton >= 0 && glfwButton < mouseButtons.length && mouseButtons[glfwButton]; }
    public int modifiers() { return modifiers; }
    public double consumeScrollX() { double value = scrollX; scrollX = 0.0; return value; }
    public double consumeScrollY() { double value = scrollY; scrollY = 0.0; return value; }

    public void clear() {
        Arrays.fill(keys, false);
        Arrays.fill(mouseButtons, false);
        scrollX = 0.0;
        scrollY = 0.0;
        modifiers = 0;
    }
}
