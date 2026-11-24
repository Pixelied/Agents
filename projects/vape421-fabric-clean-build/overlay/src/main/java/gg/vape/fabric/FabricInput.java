package gg.vape.fabric;

import gg.vape.input.GlfwToVirtualKeyCodeMap;
import gg.vape.input.InputEventDispatcher;
import gg.vape.input.KeyboardInputState;
import gg.vape.runtime.NativeBridge;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;

/** GLFW input adapter; keep the recovered client's virtual-key values and dispatcher. */
public final class FabricInput {
    private static volatile long window;
    private static final AtomicIntegerArray down = new AtomicIntegerArray(256);
    private static GLFWKeyCallback key, previousKey;
    private static GLFWCharCallback character, previousCharacter;
    private static GLFWMouseButtonCallback mouse, previousMouse;
    private static GLFWCursorPosCallback cursor, previousCursor;
    private static GLFWScrollCallback scroll, previousScroll;
    private static GLFWWindowFocusCallback focus, previousFocus;
    private FabricInput() {}

    public static void install(long handle) {
        if (handle == 0) throw new IllegalArgumentException("GLFW window is missing");
        if (window != 0) return;
        window = handle;
        key = GLFWKeyCallback.create((w, k, scan, action, mods) -> {
            int vk = virtual(k);
            if (vk > 0 && vk < down.length() && action != GLFW.GLFW_REPEAT) down.set(vk, action == GLFW.GLFW_RELEASE ? 0 : 1);
            boolean consumed = false;
            if (FabricClient.isReady() && vk > 0 && action != GLFW.GLFW_REPEAT) {
                try {
                    KeyboardInputState state = InputEventDispatcher.getInstance().getKeyboardState();
                    state.setKeyState(vk, action != GLFW.GLFW_RELEASE);
                    consumed = state.isCanceled() && action != GLFW.GLFW_RELEASE;
                } catch (Throwable e) { report(e); }
            }
            if (!consumed && previousKey != null) previousKey.invoke(w, k, scan, action, mods);
        });
        previousKey = GLFW.glfwSetKeyCallback(handle, key);
        character = GLFWCharCallback.create((w, cp) -> {
            boolean consumed = false;
            if (FabricClient.isReady()) {
                try { for (char ch : Character.toChars(cp)) consumed |= NativeBridge.om(258, ch, 0); }
                catch (Throwable e) { report(e); }
            }
            if (!consumed && previousCharacter != null) previousCharacter.invoke(w, cp);
        });
        previousCharacter = GLFW.glfwSetCharCallback(handle, character);
        cursor = GLFWCursorPosCallback.create((w, x, y) -> {
            if (FabricClient.isReady()) try { InputEventDispatcher.getInstance().getMouseState().updateCursorPosition((int)x, (int)y); } catch (Throwable e) { report(e); }
            if (previousCursor != null) previousCursor.invoke(w, x, y);
        });
        previousCursor = GLFW.glfwSetCursorPosCallback(handle, cursor);
        mouse = GLFWMouseButtonCallback.create((w, button, action, mods) -> {
            int vk = mouseVirtual(button);
            if (vk > 0) down.set(vk, action == GLFW.GLFW_RELEASE ? 0 : 1);
            boolean consumed = false;
            if (FabricClient.isReady() && button >= 0 && button <= 4) {
                try { consumed = InputEventDispatcher.getInstance().getMouseState().setButtonState(button, action != GLFW.GLFW_RELEASE); }
                catch (Throwable e) { report(e); }
            }
            if ((!consumed || action == GLFW.GLFW_RELEASE) && previousMouse != null) previousMouse.invoke(w, button, action, mods);
        });
        previousMouse = GLFW.glfwSetMouseButtonCallback(handle, mouse);
        scroll = GLFWScrollCallback.create((w, x, y) -> {
            boolean consumed = false;
            if (FabricClient.isReady()) try { consumed = InputEventDispatcher.getInstance().getMouseState().setScrollDelta((int)Math.round(y * 120)); } catch (Throwable e) { report(e); }
            if (!consumed && previousScroll != null) previousScroll.invoke(w, x, y);
        });
        previousScroll = GLFW.glfwSetScrollCallback(handle, scroll);
        focus = GLFWWindowFocusCallback.create((w, focused) -> {
            if (!focused) {
                for (int i=0;i<down.length();i++) {
                    down.set(i,0);
                    if (FabricClient.isReady()) InputEventDispatcher.getInstance().getKeyboardState().releaseKey(i);
                }
            }
            if (FabricClient.isReady()) try { NativeBridge.om(focused ? 7 : 8, w, 0); } catch (Throwable e) { report(e); }
            if (previousFocus != null) previousFocus.invoke(w, focused);
        });
        previousFocus = GLFW.glfwSetWindowFocusCallback(handle, focus);
    }
    private static int mouseVirtual(int button) {
        switch (button) { case 0:return 1; case 1:return 2; case 2:return 4; case 3:return 5; case 4:return 6; default:return 0; }
    }
    private static int virtual(int key) {
        if (key < 0) return 0;
        if (key >= 48 && key <= 57 || key >= 65 && key <= 90) return key;
        if (key == 343) return 91;
        if (key == 347) return 92;
        return GlfwToVirtualKeyCodeMap.toVirtualKey(key);
    }
    public static short keyState(int vk) {
        if (vk == 16) return (short)((down.get(160) | down.get(161)) * 256);
        if (vk == 17) return (short)((down.get(162) | down.get(163)) * 256);
        if (vk == 18) return (short)((down.get(164) | down.get(165)) * 256);
        return (short)(vk > 0 && vk < down.length() && down.get(vk) != 0 ? 256 : 0);
    }
    public static int mapKey(int key, int mode) {
        if (mode == 2) {
            if (key >= 32 && key <= 126) return key;
            return 0;
        }
        // Keep the existing key-name protocol internal: virtual codes serve as scan tokens.
        return key & 255;
    }
    public static String keyName(long packed) {
        int vk = (int)(packed >>> 16) & 255;
        if (vk >= 48 && vk <= 57 || vk >= 65 && vk <= 90) return String.valueOf((char)vk);
        if (vk >= 112 && vk <= 135) return "F" + (vk - 111);
        switch(vk) {
            case 8:return "BACKSPACE"; case 9:return "TAB"; case 13:return "ENTER";
            case 27:return "ESCAPE"; case 32:return "SPACE"; case 33:return "PAGE UP"; case 34:return "PAGE DOWN";
            case 35:return "END"; case 36:return "HOME"; case 37:return "LEFT"; case 38:return "UP";
            case 39:return "RIGHT"; case 40:return "DOWN"; case 45:return "INSERT"; case 46:return "DELETE";
            case 160:return "LSHIFT"; case 161:return "RSHIFT"; case 162:return "LCTRL"; case 163:return "RCTRL";
            case 164:return "LALT"; case 165:return "RALT"; case 91:return "LSUPER"; case 92:return "RSUPER";
            case 186:return ";"; case 187:return "="; case 188:return ","; case 189:return "-"; case 190:return ".";
            case 191:return "/"; case 192:return "`"; case 219:return "["; case 220:return "\\"; case 221:return "]"; case 222:return "'";
            default:return "KEY " + vk;
        }
    }
    public static void copy(String text) {
        if (text != null) FabricClient.execute(() -> GLFW.glfwSetClipboardString(window, text));
    }
    public static void mouseMessage(int mask, int message) {
        int b;
        int action;
        switch(message) {
            case 513:b=0;action=GLFW.GLFW_PRESS;break; case 514:b=0;action=GLFW.GLFW_RELEASE;break;
            case 516:b=1;action=GLFW.GLFW_PRESS;break; case 517:b=1;action=GLFW.GLFW_RELEASE;break;
            case 519:b=2;action=GLFW.GLFW_PRESS;break; case 520:b=2;action=GLFW.GLFW_RELEASE;break;
            case 523:b=mask==32?3:4;action=GLFW.GLFW_PRESS;break; case 524:b=mask==32?3:4;action=GLFW.GLFW_RELEASE;break;
            default:return;
        }
        // Dispatch through Minecraft's original callback, never inject OS-wide mouse events.
        FabricClient.execute(() -> { if (previousMouse != null) previousMouse.invoke(window, b, action, 0); });
    }
    private static void report(Throwable error) { System.err.println("[Vape421] input adapter: " + error); }
}
