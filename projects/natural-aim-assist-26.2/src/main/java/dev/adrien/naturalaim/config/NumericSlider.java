package dev.adrien.naturalaim.config;

import dev.adrien.naturalaim.AimMath;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

final class NumericSlider extends AbstractSliderButton {
    private final String label;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> formatter;
    private final DoubleConsumer consumer;

    NumericSlider(
            int x,
            int y,
            int width,
            int height,
            String label,
            double current,
            double min,
            double max,
            double step,
            DoubleFunction<String> formatter,
            DoubleConsumer consumer
    ) {
        super(x, y, width, height, Component.empty(), normalize(current, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.consumer = consumer;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + ": " + formatter.apply(actualValue())));
    }

    @Override
    protected void applyValue() {
        double snapped = snap(min + value * (max - min));
        value = normalize(snapped, min, max);
        consumer.accept(snapped);
        updateMessage();
    }

    void setActualValue(double actual) {
        value = normalize(snap(actual), min, max);
        updateMessage();
    }

    private double actualValue() {
        return snap(min + value * (max - min));
    }

    private double snap(double actual) {
        double clamped = AimMath.clamp(actual, min, max);
        if (step <= 0.0) return clamped;
        double snapped = min + Math.round((clamped - min) / step) * step;
        return AimMath.clamp(snapped, min, max);
    }

    private static double normalize(double actual, double min, double max) {
        if (max <= min) return 0.0;
        return AimMath.clamp((actual - min) / (max - min), 0.0, 1.0);
    }
}
