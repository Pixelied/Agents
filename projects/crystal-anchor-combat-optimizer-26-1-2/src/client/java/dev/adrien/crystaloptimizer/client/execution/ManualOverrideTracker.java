package dev.adrien.crystaloptimizer.client.execution;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

/** Observable user-control gate. It never mutates pending bot reconciliation state. */
public final class ManualOverrideTracker {
    private final BooleanSupplier controllingInput;
    private final boolean testMutable;
    private volatile boolean testOverride;

    private ManualOverrideTracker(BooleanSupplier controllingInput, boolean testMutable) {
        this.controllingInput = Objects.requireNonNull(controllingInput, "controllingInput");
        this.testMutable = testMutable;
    }

    public static ManualOverrideTracker live(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        return new ManualOverrideTracker(
            () -> minecraft.options.keyAttack.isDown() || minecraft.options.keyUse.isDown(),
            false
        );
    }

    public static ManualOverrideTracker forTests() {
        return new ManualOverrideTracker(() -> false, true);
    }

    public boolean isUserControllingCombatInput() {
        return testMutable ? testOverride : controllingInput.getAsBoolean();
    }

    public void setUserControllingCombatInput(boolean controlling) {
        if (!testMutable) {
            throw new IllegalStateException("live manual override is read from Minecraft input state");
        }
        testOverride = controlling;
    }
}
