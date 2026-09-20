package dev.adrien.togglesneak;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ToggleSneakHud {
    private static final HudAnimationModel ANIMATION = new HudAnimationModel();

    private ToggleSneakHud() {
    }

    static void extract(GuiGraphicsExtractor graphics) {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }

        final HudAnimationModel.Frame frame = ANIMATION.update(
                ToggleSneakClient.state().hudState(),
                System.nanoTime() / 1_000_000L
        );

        if (!frame.visible() || frame.alpha() <= 0.0F) {
            return;
        }

        final int alpha = Math.max(0, Math.min(255, Math.round(frame.alpha() * 255.0F)));
        final int color = (alpha << 24) | 0x00FFFFFF;
        final int x = (graphics.guiWidth() - minecraft.font.width(frame.text())) / 2;
        final int y = graphics.guiHeight() - 52 + Math.round(frame.slideY());

        graphics.text(minecraft.font, frame.text(), x, y, color, true);
    }

    static void resetAnimation() {
        ANIMATION.reset();
    }
}
