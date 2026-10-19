package dev.pixelied.maceswap;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Consumer;

/** Pure vanilla 26.3 widgets, no Cloth Config dependency. */
public final class ConfigScreen extends Screen {
    private static final String[] TABS = { "General", "Prediction", "Recovery" };
    private final Screen parent;
    private SwapConfig working;
    private int tab = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Predictive Elytra Mace Swap"));
        this.parent = parent;
        working = PredictiveMaceSwapClient.config().copy();
    }

    @Override
    protected void init() {
        int widthTotal = Math.min(420, Math.max(264, width - 26));
        int left = (width - widthTotal) / 2;
        int tabGap = 4;
        int tw = (widthTotal - 2 * tabGap) / 3;
        for (int i = 0; i < TABS.length; i++) {
            final int chosen = i;
            addRenderableWidget(Button.builder(Component.literal((tab == i ? "• " : "") + TABS[i]), b -> {
                tab = chosen;
                rebuildWidgets();
            }).bounds(left + (tw + tabGap) * i, 62, tw, 20).build());
        }
        int colGap = 12;
        int colWidth = (widthTotal - colGap) / 2;
        int firstY = 107;
        switch (tab) {
            case 0 -> {
                addBool(left, firstY, colWidth, "Enabled", "Look for smash opportunities",
                    () -> working.enabled, v -> working.enabled = v);
                addBool(left + colWidth + colGap, firstY, colWidth, "Require aim", "Aim ray must intersect target",
                    () -> working.requireAim, v -> working.requireAim = v);
                addBool(left, firstY + 56, colWidth, "Debug actionbar", "Show live state and recovery",
                    () -> working.debugOverlay, v -> working.debugOverlay = v);
                addNumber(left + colWidth + colGap, firstY + 56, colWidth, "Target radius", "Nearby target search area",
                    () -> working.searchRadius, n -> working.searchRadius = n, 6, 24, 2, " blocks");
            }
            case 1 -> {
                addNumber(left, firstY, colWidth, "Lookahead", "Maximum flight forecast",
                    () -> working.predictionTicks, n -> working.predictionTicks = n, 8, 32, 2, " ticks");
                addNumber(left + colWidth + colGap, firstY, colWidth, "Extra lead", "Extra swap preparation",
                    () -> working.extraLeadTicks, n -> working.extraLeadTicks = n, 0, 8, 1, " ticks");
                addNumber(left, firstY + 56, colWidth, "Smash window", "Time to attack after exit",
                    () -> working.opportunityTicks, n -> working.opportunityTicks = n, 2, 10, 1, " ticks");
                addNumber(left + colWidth + colGap, firstY + 56, colWidth, "Range margin", "Conservative attack reach",
                    () -> working.rangeSafetyPercent, n -> working.rangeSafetyPercent = n, 0, 45, 3, "%");
            }
            default -> {
                addBool(left, firstY, colWidth, "Resume gliding", "Auto-request glide after restore",
                    () -> working.automaticRecovery, v -> working.automaticRecovery = v);
                addNumber(left + colWidth + colGap, firstY, colWidth, "Latency floor", "Min. assumed network delay",
                    () -> working.latencyFloorMs, n -> working.latencyFloorMs = n, 0, 500, 25, " ms");
                addNumber(left, firstY + 56, colWidth, "Glide retries", "Maximum re-glide requests",
                    () -> working.recoveryRetries, n -> working.recoveryRetries = n, 1, 20, 1, "");
                addNumber(left + colWidth + colGap, firstY + 56, colWidth, "Cooldown", "Delay between opportunities",
                    () -> working.cooldownTicks, n -> working.cooldownTicks = n, 5, 60, 5, " ticks");
            }
        }
        int bottom = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"), b -> {
            working = new SwapConfig();
            rebuildWidgets();
        }).bounds(left, bottom, Math.max(90, widthTotal / 3), 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save & Done"), b -> onClose())
            .bounds(left + widthTotal - Math.max(100, widthTotal / 3), bottom, Math.max(100, widthTotal / 3), 20).build());
    }

    private void addBool(int x, int y, int w, String label, String detail, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(boolLabel(label, get.getAsBoolean()), button -> {
            boolean next = !get.getAsBoolean();
            set.accept(next);
            button.setMessage(boolLabel(label, next));
        }).bounds(x, y, w, 20).build());
    }

    private Component boolLabel(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    private void addNumber(int x, int y, int w, String label, String detail,
                           IntSupplier get, IntConsumer set, int min, int max, int step, String suffix) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + get.getAsInt() + suffix), button -> {
            int next = get.getAsInt() + step;
            if (next > max) next = min;
            set.accept(next);
            button.setMessage(Component.literal(label + ": " + next + suffix));
        }).bounds(x, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        int total = Math.min(420, Math.max(264, width - 26));
        int x = (width - total) / 2;
        graphics.fill(x - 7, 47, x + total + 7, 51, 0xFF638D9A);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 17,
            Component.literal("PREDICTIVE ELYTRA MACE SWAP").withStyle(ChatFormatting.WHITE));
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 35,
            Component.literal("Minecraft 26.3  •  Manual attacks  •  Safe inventory swaps").withStyle(ChatFormatting.GRAY));
        graphics.textRenderer().accept(x, 91,
            Component.literal(tab == 0 ? "CORE BEHAVIOR" : tab == 1 ? "TRAJECTORY & TIMING" : "SAFETY & RECOVERY")
                .withStyle(ChatFormatting.AQUA));
        String[] descriptions = tab == 0
            ? new String[]{"Scan for a valid smash", "Only swap when on-target", "Show state over hotbar", "Maximum search distance"}
            : tab == 1
                ? new String[]{"Simulate ahead", "Allow for packet timing", "How long to stay armed", "Avoid edge-of-reach hits"}
                : new String[]{"Retry Space action safely", "Minimum assumed RTT", "Limit glide retries", "Prevent swap loops"};
        int gap = 12;
        int colW = (total - gap) / 2;
        graphics.textRenderer().accept(x + 3, 130, Component.literal(descriptions[0]).withStyle(ChatFormatting.GRAY));
        graphics.textRenderer().accept(x + colW + gap + 3, 130, Component.literal(descriptions[1]).withStyle(ChatFormatting.GRAY));
        graphics.textRenderer().accept(x + 3, 186, Component.literal(descriptions[2]).withStyle(ChatFormatting.GRAY));
        graphics.textRenderer().accept(x + colW + gap + 3, 186, Component.literal(descriptions[3]).withStyle(ChatFormatting.GRAY));
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, height - 47,
            Component.literal("Status: " + PredictiveMaceSwapClient.status()).withStyle(ChatFormatting.GRAY));
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        PredictiveMaceSwapClient.applyConfig(working);
        minecraft.gui.setScreen(parent);
    }
}
