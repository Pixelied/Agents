package dev.adrien.togglesneak;

public final class StateSimulation {
    private static int assertions;

    public static void main(String[] args) {
        testBackslashToggleAndRepeatProtection();
        testPhysicalSneakDisablesToggleAndBlocksSamePress();
        testMovingSmartLatch();
        testStationarySmartLatch();
        testMovementChangeKeepsProgress();
        testReleaseToLock();
        testGuiCancelsLatchAndBlocksHeldSneak();
        testGuiSuspendsForcedSneakButPreservesLogicalState();
        testSessionResets();
        testHudFadeInPersistentAndFadeOut();
        testHudTextChangesDoNotCrashOrRestartLatchAnimation();

        System.out.println("ToggleSneak simulations passed: " + assertions + " assertions");
    }

    private static void testBackslashToggleAndRepeatProtection() {
        ToggleSneakState state = new ToggleSneakState();

        state.tick(true, false, false, false, true);
        check(state.isToggleOn(), "backslash press turns toggle on");

        for (int i = 0; i < 30; i++) {
            state.tick(true, false, false, false, true);
        }
        check(state.isToggleOn(), "held/repeated backslash does not retrigger");

        state.tick(false, false, false, false, true);
        state.tick(true, false, false, false, true);
        check(!state.isToggleOn(), "second physical backslash press turns toggle off");
    }

    private static void testPhysicalSneakDisablesToggleAndBlocksSamePress() {
        ToggleSneakState state = toggledOnState();

        state.tick(false, true, true, false, true);
        check(!state.isToggleOn(), "physical Sneak immediately disables toggle");
        check(state.isLatchBlockedUntilSneakRelease(), "same Sneak press is latch-blocked");

        for (int i = 0; i < 240; i++) {
            state.tick(false, true, true, false, true);
        }
        check(!state.isReleaseToLock(), "same held Sneak press cannot relatch");

        state.tick(false, false, false, false, true);
        check(!state.isLatchBlockedUntilSneakRelease(), "release clears latch block");

        int ticks = ticksUntilReady(state, true, 100);
        check(ticks >= 65 && ticks <= 75, "fresh Sneak press can Smart Latch after release");
    }

    private static void testMovingSmartLatch() {
        ToggleSneakState state = new ToggleSneakState();
        int ticks = ticksUntilReady(state, true, 100);

        check(ticks >= 65 && ticks <= 75, "moving latch completes in roughly 3.5 seconds");
        check(!state.isToggleOn(), "ready latch does not enable before release");
        check(state.hudState().text().equals("Release Sneak to lock"), "ready HUD asks for release");
    }

    private static void testStationarySmartLatch() {
        ToggleSneakState state = new ToggleSneakState();
        int ticks = ticksUntilReady(state, false, 220);

        check(ticks >= 155 && ticks <= 165, "stationary latch completes in roughly 8 seconds");
        check(!state.isToggleOn(), "stationary latch also waits for release");
    }

    private static void testMovementChangeKeepsProgress() {
        ToggleSneakState state = new ToggleSneakState();

        for (int i = 0; i < 40; i++) {
            state.tick(false, true, false, false, true);
        }

        double before = state.latchProgress();
        double blendBefore = state.movementBlend();
        check(before > 0.20 && before < 0.30, "stationary partial hold builds confidence");

        state.tick(false, true, true, false, true);
        check(state.latchProgress() > before, "starting movement keeps and advances confidence");
        check(state.movementBlend() > blendBefore && state.movementBlend() < 1.0,
                "movement influence changes smoothly instead of snapping");

        int extra = 0;
        while (!state.isReleaseToLock() && extra < 140) {
            state.tick(false, true, true, false, true);
            extra++;
        }

        check(state.isReleaseToLock(), "mixed movement hold eventually becomes ready");
        check(40 + extra < 160, "moving mid-hold finishes before stationary-only timing");
    }

    private static void testReleaseToLock() {
        ToggleSneakState state = new ToggleSneakState();
        ticksUntilReady(state, true, 100);

        check(!state.isToggleOn(), "ready state still physically sneaks only from held Shift");
        state.tick(false, false, false, false, true);
        check(state.isToggleOn(), "releasing Sneak after ready locks Toggle Sneak on");
        check(state.hudState().text().equals("Toggle Sneak: ON"), "release-to-lock switches HUD to ON");
    }

    private static void testGuiCancelsLatchAndBlocksHeldSneak() {
        ToggleSneakState state = new ToggleSneakState();

        for (int i = 0; i < 35; i++) {
            state.tick(false, true, true, false, true);
        }
        check(state.latchProgress() > 0.0, "latch has progress before GUI opens");

        state.tick(false, true, true, true, true);
        check(state.latchProgress() == 0.0, "opening GUI cancels active latch");
        check(state.isLatchBlockedUntilSneakRelease(), "Sneak held through GUI is blocked");

        for (int i = 0; i < 120; i++) {
            state.tick(false, true, true, false, true);
        }
        check(!state.isReleaseToLock(), "closing GUI while still holding Sneak does not resume same attempt");

        state.tick(false, false, false, false, true);
        int freshTicks = ticksUntilReady(state, true, 100);
        check(freshTicks >= 65 && freshTicks <= 75, "fresh post-GUI Sneak press can latch normally");
    }

    private static void testGuiSuspendsForcedSneakButPreservesLogicalState() {
        ToggleSneakState state = toggledOnState();

        check(state.shouldForceSneak(false, false, true), "toggle forces sneak during gameplay");
        state.tick(false, false, false, true, true);
        check(state.isToggleOn(), "GUI preserves logical toggle state");
        check(!state.shouldForceSneak(true, false, true), "GUI suspends forced sneak");
        check(state.shouldForceSneak(false, false, true), "closing GUI resumes forced sneak");
        check(!state.shouldForceSneak(false, true, true), "riding suspends forced sneak");
        check(!state.shouldForceSneak(false, false, false), "dead player is never force-sneaked");
    }

    private static void testSessionResets() {
        ToggleSneakState state = toggledOnState();
        state.resetForSessionChange();
        check(!state.isToggleOn(), "death reset clears toggle");

        state = toggledOnState();
        state.resetForSessionChange();
        check(!state.isToggleOn(), "disconnect reset clears toggle");

        state = toggledOnState();
        state.resetForSessionChange();
        check(!state.isToggleOn(), "world-change reset clears toggle");
        check(state.hudState().mode() == ToggleSneakState.HudMode.NONE, "reset clears HUD state");
    }

    private static void testHudFadeInPersistentAndFadeOut() {
        HudAnimationModel animation = new HudAnimationModel();
        ToggleSneakState.HudState on =
                new ToggleSneakState.HudState(ToggleSneakState.HudMode.ON, "Toggle Sneak: ON");

        HudAnimationModel.Frame start = animation.update(on, 1_000L);
        check(start.visible(), "HUD is visible when ON starts");
        check(start.alpha() == 0.0F, "HUD fade-in starts transparent");
        check(start.slideY() > 0.0F, "HUD starts slightly below its final position");

        HudAnimationModel.Frame middle = animation.update(on, 1_090L);
        check(middle.alpha() > 0.0F && middle.alpha() < 1.0F, "HUD fades in smoothly");

        HudAnimationModel.Frame settled = animation.update(on, 1_220L);
        check(near(settled.alpha(), 1.0F), "HUD reaches full alpha after fade-in");
        check(near(settled.slideY(), 0.0F), "HUD upward slide settles");

        HudAnimationModel.Frame persistent = animation.update(on, 4_000L);
        check(near(persistent.alpha(), 1.0F), "ON HUD remains subtly persistent");

        ToggleSneakState.HudState none =
                new ToggleSneakState.HudState(ToggleSneakState.HudMode.NONE, "");
        animation.update(none, 4_000L);
        HudAnimationModel.Frame fade = animation.update(none, 4_150L);
        check(fade.visible() && fade.alpha() > 0.0F && fade.alpha() < 1.0F,
                "HUD fades out softly");

        HudAnimationModel.Frame gone = animation.update(none, 4_350L);
        check(!gone.visible(), "HUD is gone after fade-out");
    }

    private static void testHudTextChangesDoNotCrashOrRestartLatchAnimation() {
        HudAnimationModel animation = new HudAnimationModel();

        for (int i = 0; i < 30; i++) {
            String text = "Hold Sneak to lock: " + (3.0 - i * 0.1) + "s";
            HudAnimationModel.Frame frame = animation.update(
                    new ToggleSneakState.HudState(ToggleSneakState.HudMode.LATCH_HOLD, text),
                    10_000L + i * 50L
            );
            check(frame.visible(), "countdown text update remains renderable");
        }

        HudAnimationModel.Frame ready = animation.update(
                new ToggleSneakState.HudState(
                        ToggleSneakState.HudMode.LATCH_READY,
                        "Release Sneak to lock"
                ),
                11_600L
        );
        check(ready.visible(), "ready text change does not crash");
        check(near(ready.alpha(), 1.0F), "latch text changes do not restart fade-in");
    }

    private static ToggleSneakState toggledOnState() {
        ToggleSneakState state = new ToggleSneakState();
        state.tick(true, false, false, false, true);
        state.tick(false, false, false, false, true);
        check(state.isToggleOn(), "test setup toggles ON");
        return state;
    }

    private static int ticksUntilReady(ToggleSneakState state, boolean moving, int maxTicks) {
        int ticks = 0;
        while (!state.isReleaseToLock() && ticks < maxTicks) {
            state.tick(false, true, moving, false, true);
            ticks++;
        }
        check(state.isReleaseToLock(), "Smart Latch reached release-ready state");
        return ticks;
    }

    private static boolean near(float value, float expected) {
        return Math.abs(value - expected) < 0.001F;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
