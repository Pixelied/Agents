package dev.adrien.togglesneak;

import java.util.Locale;

/**
 * Pure state machine for ToggleSneak. Minecraft-specific input is adapted by
 * {@link ToggleSneakClient}; keeping this class game-agnostic makes its edge
 * cases deterministic and simulation-testable.
 */
public final class ToggleSneakState {
    static final int HUD_INTENTIONAL_HOLD_TICKS = 9;
    static final int OFF_MESSAGE_TICKS = 14;

    private static final double MOVING_SECONDS = 3.5;
    private static final double STATIONARY_SECONDS = 8.0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MOVEMENT_SMOOTHING = 0.12;

    private boolean toggleOn;
    private boolean previousToggleKeyDown;
    private boolean previousSneakDown;
    private boolean latchBlockedUntilSneakRelease;

    private boolean latchActive;
    private boolean releaseToLock;
    private int holdTicks;
    private double latchProgress;
    private double movementBlend;
    private int offMessageTicks;

    public void tick(
            boolean toggleKeyDown,
            boolean physicalSneakDown,
            boolean moving,
            boolean screenOpen,
            boolean latchContextAllowed
    ) {
        if (offMessageTicks > 0) {
            offMessageTicks--;
        }

        // A GUI owns keyboard focus. Preserve the logical toggle state, but do
        // not let typing/GUI shortcuts toggle this mod or continue a latch.
        if (screenOpen) {
            cancelLatch();
            latchBlockedUntilSneakRelease = physicalSneakDown;
            previousToggleKeyDown = toggleKeyDown;
            previousSneakDown = physicalSneakDown;
            return;
        }

        final boolean togglePressed = toggleKeyDown && !previousToggleKeyDown;
        final boolean sneakPressed = physicalSneakDown && !previousSneakDown;
        final boolean sneakReleased = !physicalSneakDown && previousSneakDown;
        final boolean wasToggleOn = toggleOn;

        if (togglePressed) {
            setToggleOn(!toggleOn);
            cancelLatch();

            // Turning the toggle off while Shift is already held must not let
            // that same physical press immediately become a Smart Latch.
            if (!toggleOn && physicalSneakDown) {
                latchBlockedUntilSneakRelease = true;
            }
        }

        // Only a new physical Sneak press disables an existing logical toggle.
        // If the user toggles ON while Shift was already held, releasing Shift
        // simply leaves Toggle Sneak ON.
        if (wasToggleOn && sneakPressed) {
            setToggleOn(false);
            cancelLatch();
            latchBlockedUntilSneakRelease = true;
        }

        if (sneakReleased) {
            if (releaseToLock && !latchBlockedUntilSneakRelease && latchContextAllowed) {
                setToggleOn(true);
            }

            cancelLatch();
            latchBlockedUntilSneakRelease = false;
            finishTick(toggleKeyDown, physicalSneakDown);
            return;
        }

        if (!latchContextAllowed) {
            cancelLatch();
            if (physicalSneakDown) {
                latchBlockedUntilSneakRelease = true;
            } else {
                latchBlockedUntilSneakRelease = false;
            }
            finishTick(toggleKeyDown, physicalSneakDown);
            return;
        }

        if (toggleOn || !physicalSneakDown || latchBlockedUntilSneakRelease) {
            if (!physicalSneakDown) {
                cancelLatch();
            }
            finishTick(toggleKeyDown, physicalSneakDown);
            return;
        }

        if (!latchActive) {
            latchActive = true;
            releaseToLock = false;
            holdTicks = 0;
            latchProgress = 0.0;
            // Starting from the current motion state gives the advertised
            // ~3.5s / ~8s endpoints. Changes after this are smoothed.
            movementBlend = moving ? 1.0 : 0.0;
        }

        final double targetBlend = moving ? 1.0 : 0.0;
        movementBlend += (targetBlend - movementBlend) * MOVEMENT_SMOOTHING;

        if (!releaseToLock) {
            holdTicks++;
            final double secondsForFullConfidence = effectiveLatchSeconds();
            latchProgress = Math.min(
                    1.0,
                    latchProgress + 1.0 / (secondsForFullConfidence * TICKS_PER_SECOND)
            );

            if (latchProgress >= 1.0) {
                releaseToLock = true;
            }
        }

        finishTick(toggleKeyDown, physicalSneakDown);
    }

    public void resetForSessionChange() {
        toggleOn = false;
        previousToggleKeyDown = false;
        previousSneakDown = false;
        latchBlockedUntilSneakRelease = false;
        offMessageTicks = 0;
        cancelLatch();
    }

    public boolean isToggleOn() {
        return toggleOn;
    }

    public boolean isReleaseToLock() {
        return releaseToLock;
    }

    public boolean isLatchBlockedUntilSneakRelease() {
        return latchBlockedUntilSneakRelease;
    }

    public double latchProgress() {
        return latchProgress;
    }

    public double movementBlend() {
        return movementBlend;
    }

    public boolean shouldForceSneak(boolean screenOpen, boolean passenger, boolean playerAlive) {
        return toggleOn && !screenOpen && !passenger && playerAlive;
    }

    public HudState hudState() {
        if (releaseToLock) {
            return new HudState(HudMode.LATCH_READY, "Release Sneak to lock");
        }

        if (latchActive && holdTicks >= HUD_INTENTIONAL_HOLD_TICKS) {
            final double remaining = Math.max(0.0, (1.0 - latchProgress) * effectiveLatchSeconds());
            return new HudState(
                    HudMode.LATCH_HOLD,
                    String.format(Locale.ROOT, "Hold Sneak to lock: %.1fs", remaining)
            );
        }

        if (offMessageTicks > 0) {
            return new HudState(HudMode.OFF, "Toggle Sneak: OFF");
        }

        if (toggleOn) {
            return new HudState(HudMode.ON, "Toggle Sneak: ON");
        }

        return new HudState(HudMode.NONE, "");
    }

    private void setToggleOn(boolean enabled) {
        if (toggleOn == enabled) {
            return;
        }

        toggleOn = enabled;
        if (enabled) {
            offMessageTicks = 0;
        } else {
            offMessageTicks = OFF_MESSAGE_TICKS;
        }
    }

    private double effectiveLatchSeconds() {
        return STATIONARY_SECONDS + (MOVING_SECONDS - STATIONARY_SECONDS) * movementBlend;
    }

    private void cancelLatch() {
        latchActive = false;
        releaseToLock = false;
        holdTicks = 0;
        latchProgress = 0.0;
        movementBlend = 0.0;
    }

    private void finishTick(boolean toggleKeyDown, boolean physicalSneakDown) {
        previousToggleKeyDown = toggleKeyDown;
        previousSneakDown = physicalSneakDown;
    }

    public enum HudMode {
        NONE,
        ON,
        OFF,
        LATCH_HOLD,
        LATCH_READY;

        boolean sameVisualFamily(HudMode other) {
            return this == other
                    || (isLatchMode(this) && isLatchMode(other));
        }

        private static boolean isLatchMode(HudMode mode) {
            return mode == LATCH_HOLD || mode == LATCH_READY;
        }
    }

    public record HudState(HudMode mode, String text) {
    }
}
