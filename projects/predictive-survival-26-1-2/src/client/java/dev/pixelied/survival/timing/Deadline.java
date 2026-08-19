package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;

import java.util.Objects;

public record Deadline(TickWindow completionWindow) {
    public Deadline {
        completionWindow = Objects.requireNonNull(completionWindow, "completionWindow");
    }

    public boolean completesBefore(TickWindow impact) {
        Objects.requireNonNull(impact, "impact");
        return completionWindow.latest() <= impact.earliest();
    }
}
