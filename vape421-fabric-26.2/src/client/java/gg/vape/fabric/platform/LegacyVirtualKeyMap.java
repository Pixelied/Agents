package gg.vape.fabric.platform;

import org.lwjgl.glfw.GLFW;

final class LegacyVirtualKeyMap {
    private LegacyVirtualKeyMap() { }

    static int mouseButton(int vk) {
        return switch (vk) {
            case 1 -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case 2 -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case 4 -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            case 5 -> GLFW.GLFW_MOUSE_BUTTON_4;
            case 6 -> GLFW.GLFW_MOUSE_BUTTON_5;
            default -> -1;
        };
    }

    static int key(int vk) {
        if (vk >= '0' && vk <= '9') return vk;
        if (vk >= 'A' && vk <= 'Z') return vk;
        if (vk >= 112 && vk <= 135) return GLFW.GLFW_KEY_F1 + (vk - 112);
        if (vk >= 96 && vk <= 105) return GLFW.GLFW_KEY_KP_0 + (vk - 96);
        return switch (vk) {
            case 8 -> GLFW.GLFW_KEY_BACKSPACE;
            case 9 -> GLFW.GLFW_KEY_TAB;
            case 13 -> GLFW.GLFW_KEY_ENTER;
            case 16, 160 -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case 161 -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case 17, 162 -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case 163 -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case 18, 164 -> GLFW.GLFW_KEY_LEFT_ALT;
            case 165 -> GLFW.GLFW_KEY_RIGHT_ALT;
            case 19 -> GLFW.GLFW_KEY_PAUSE;
            case 20 -> GLFW.GLFW_KEY_CAPS_LOCK;
            case 27 -> GLFW.GLFW_KEY_ESCAPE;
            case 32 -> GLFW.GLFW_KEY_SPACE;
            case 33 -> GLFW.GLFW_KEY_PAGE_UP;
            case 34 -> GLFW.GLFW_KEY_PAGE_DOWN;
            case 35 -> GLFW.GLFW_KEY_END;
            case 36 -> GLFW.GLFW_KEY_HOME;
            case 37 -> GLFW.GLFW_KEY_LEFT;
            case 38 -> GLFW.GLFW_KEY_UP;
            case 39 -> GLFW.GLFW_KEY_RIGHT;
            case 40 -> GLFW.GLFW_KEY_DOWN;
            case 45 -> GLFW.GLFW_KEY_INSERT;
            case 46 -> GLFW.GLFW_KEY_DELETE;
            case 106 -> GLFW.GLFW_KEY_KP_MULTIPLY;
            case 107 -> GLFW.GLFW_KEY_KP_ADD;
            case 109 -> GLFW.GLFW_KEY_KP_SUBTRACT;
            case 110 -> GLFW.GLFW_KEY_KP_DECIMAL;
            case 111 -> GLFW.GLFW_KEY_KP_DIVIDE;
            case 144 -> GLFW.GLFW_KEY_NUM_LOCK;
            case 145 -> GLFW.GLFW_KEY_SCROLL_LOCK;
            case 186 -> GLFW.GLFW_KEY_SEMICOLON;
            case 187 -> GLFW.GLFW_KEY_EQUAL;
            case 188 -> GLFW.GLFW_KEY_COMMA;
            case 189 -> GLFW.GLFW_KEY_MINUS;
            case 190 -> GLFW.GLFW_KEY_PERIOD;
            case 191 -> GLFW.GLFW_KEY_SLASH;
            case 192 -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case 219 -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case 220 -> GLFW.GLFW_KEY_BACKSLASH;
            case 221 -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case 222 -> GLFW.GLFW_KEY_APOSTROPHE;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }
}
