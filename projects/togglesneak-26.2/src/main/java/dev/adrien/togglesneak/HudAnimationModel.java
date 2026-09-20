package dev.adrien.togglesneak;

/**
 * Small real-time animation model shared by the HUD renderer and simulations.
 * Text changes inside the same Smart Latch phase do not restart the animation.
 */
public final class HudAnimationModel {
    static final long FADE_IN_MS = 180L;
    static final long FADE_OUT_MS = 300L;
    static final float APPEAR_SLIDE_PX = 4.0F;

    private ToggleSneakState.HudMode displayedMode = ToggleSneakState.HudMode.NONE;
    private String displayedText = "";
    private long appearStartMs;
    private boolean fadingOut;
    private long fadeOutStartMs;
    private float fadeOutStartAlpha;

    public Frame update(ToggleSneakState.HudState requested, long nowMs) {
        if (requested.mode() != ToggleSneakState.HudMode.NONE) {
            final boolean continueExisting =
                    displayedMode != ToggleSneakState.HudMode.NONE
                            && !fadingOut
                            && displayedMode.sameVisualFamily(requested.mode());

            if (!continueExisting) {
                displayedMode = requested.mode();
                appearStartMs = nowMs;
            } else {
                displayedMode = requested.mode();
            }

            displayedText = requested.text();
            fadingOut = false;
        } else if (displayedMode != ToggleSneakState.HudMode.NONE && !fadingOut) {
            fadeOutStartAlpha = fadeInAlpha(nowMs);
            fadeOutStartMs = nowMs;
            fadingOut = true;
        }

        if (displayedMode == ToggleSneakState.HudMode.NONE) {
            return Frame.hidden();
        }

        if (fadingOut) {
            final float progress = clamp01((nowMs - fadeOutStartMs) / (float) FADE_OUT_MS);
            final float alpha = fadeOutStartAlpha * (1.0F - smoothStep(progress));

            if (progress >= 1.0F) {
                displayedMode = ToggleSneakState.HudMode.NONE;
                displayedText = "";
                fadingOut = false;
                return Frame.hidden();
            }

            return new Frame(displayedText, alpha, 0.0F, true);
        }

        final float inProgress = clamp01((nowMs - appearStartMs) / (float) FADE_IN_MS);
        final float alpha = smoothStep(inProgress);
        final float slide = APPEAR_SLIDE_PX * (1.0F - easeOutCubic(inProgress));
        return new Frame(displayedText, alpha, slide, true);
    }

    public void reset() {
        displayedMode = ToggleSneakState.HudMode.NONE;
        displayedText = "";
        appearStartMs = 0L;
        fadingOut = false;
        fadeOutStartMs = 0L;
        fadeOutStartAlpha = 0.0F;
    }

    private float fadeInAlpha(long nowMs) {
        final float progress = clamp01((nowMs - appearStartMs) / (float) FADE_IN_MS);
        return smoothStep(progress);
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float easeOutCubic(float value) {
        final float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record Frame(String text, float alpha, float slideY, boolean visible) {
        static Frame hidden() {
            return new Frame("", 0.0F, 0.0F, false);
        }
    }
}
