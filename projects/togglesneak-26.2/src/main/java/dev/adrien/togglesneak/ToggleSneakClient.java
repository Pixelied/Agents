package dev.adrien.togglesneak;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

public final class ToggleSneakClient implements ClientModInitializer {
    public static final String MOD_ID = "togglesneak";

    private static final ToggleSneakState STATE = new ToggleSneakState();
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "toggle_sneak")
    );

    private static KeyMapping toggleKey;
    private static ClientLevel lastLevel;
    private static LocalPlayer lastPlayer;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.togglesneak.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSLASH,
                CATEGORY
        ));

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "status"),
                (graphics, deltaTracker) -> ToggleSneakHud.extract(graphics)
        );
    }

    public static void onClientTick(Minecraft minecraft) {
        final LocalPlayer player = minecraft.player;
        final ClientLevel level = minecraft.level;

        if (player == null || level == null) {
            if (lastPlayer != null || lastLevel != null || STATE.isToggleOn()) {
                resetSession();
            }
            lastPlayer = null;
            lastLevel = null;
            return;
        }

        if (player != lastPlayer || level != lastLevel) {
            resetSession();
            lastPlayer = player;
            lastLevel = level;
        }

        if (!player.isAlive()) {
            resetSession();
            lastPlayer = player;
            lastLevel = level;
            return;
        }

        final boolean screenOpen = minecraft.gui.screen() != null;
        final boolean moving = minecraft.options.keyUp.isDown()
                || minecraft.options.keyDown.isDown()
                || minecraft.options.keyLeft.isDown()
                || minecraft.options.keyRight.isDown();

        STATE.tick(
                toggleKey != null && toggleKey.isDown(),
                minecraft.options.keyShift.isDown(),
                moving,
                screenOpen,
                !player.isPassenger()
        );
    }

    public static boolean shouldApplyForcedSneak(KeyboardInput input) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;

        if (player == null || player.input != input) {
            return false;
        }

        return STATE.shouldForceSneak(
                minecraft.gui.screen() != null,
                player.isPassenger(),
                player.isAlive()
        );
    }

    static ToggleSneakState state() {
        return STATE;
    }

    private static void resetSession() {
        STATE.resetForSessionChange();
        ToggleSneakHud.resetAnimation();
    }
}
