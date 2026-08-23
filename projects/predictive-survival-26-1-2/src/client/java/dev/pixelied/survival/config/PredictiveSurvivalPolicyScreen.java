package dev.pixelied.survival.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Edits the staged CUSTOM rescue allow-list. The parent screen owns persistence. */
public final class PredictiveSurvivalPolicyScreen extends Screen {
    private static final int ROW_WIDTH = 310;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 24;

    private final Screen parent;
    private final SurvivalConfigDraft draft;

    private CycleButton<Boolean> deathProtectionButton;
    private CycleButton<Boolean> shieldsButton;
    private CycleButton<Boolean> consumablesButton;
    private CycleButton<Boolean> equipmentButton;
    private CycleButton<Boolean> inventoryRoutingButton;
    private CycleButton<Boolean> mainHandTakeoverButton;
    private CycleButton<Boolean> proactiveDualProtectionButton;

    public PredictiveSurvivalPolicyScreen(Screen parent, SurvivalConfigDraft draft) {
        super(Component.translatable("predictive_survival.config.policy.title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.draft = Objects.requireNonNull(draft, "draft");
    }

    @Override
    protected void init() {
        int left = (this.width - ROW_WIDTH) / 2;
        int top = Math.max(28, (this.height - 224) / 2);

        StringWidget title = new StringWidget(this.title, this.font);
        title.setX((this.width - this.font.width(this.title)) / 2);
        title.setY(top - 22);
        this.addRenderableWidget(title);

        RescuePolicy policy = this.draft.customPolicy();
        this.deathProtectionButton = addPolicyRow(left, top,
            "predictive_survival.config.policy.death_protection", policy.deathProtection(), PolicyFlag.DEATH_PROTECTION);
        this.shieldsButton = addPolicyRow(left, top + ROW_GAP,
            "predictive_survival.config.policy.shields", policy.shields(), PolicyFlag.SHIELDS);
        this.consumablesButton = addPolicyRow(left, top + ROW_GAP * 2,
            "predictive_survival.config.policy.consumables", policy.consumables(), PolicyFlag.CONSUMABLES);
        this.equipmentButton = addPolicyRow(left, top + ROW_GAP * 3,
            "predictive_survival.config.policy.equipment", policy.equipment(), PolicyFlag.EQUIPMENT);
        this.inventoryRoutingButton = addPolicyRow(left, top + ROW_GAP * 4,
            "predictive_survival.config.policy.inventory_routing", policy.inventoryRouting(), PolicyFlag.INVENTORY_ROUTING);
        this.mainHandTakeoverButton = addPolicyRow(left, top + ROW_GAP * 5,
            "predictive_survival.config.policy.main_hand_takeover", policy.mainHandTakeover(), PolicyFlag.MAIN_HAND_TAKEOVER);
        this.proactiveDualProtectionButton = addPolicyRow(left, top + ROW_GAP * 6,
            "predictive_survival.config.policy.proactive_dual_protection", policy.proactiveDualProtection(), PolicyFlag.PROACTIVE_DUAL_PROTECTION);

        int controlsY = top + ROW_GAP * 7 + 6;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> closeToParent())
            .bounds(left + 80, controlsY, 150, ROW_HEIGHT)
            .build());
        syncDependencies();
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private CycleButton<Boolean> addPolicyRow(
        int x,
        int y,
        String label,
        boolean initial,
        PolicyFlag flag
    ) {
        String description = label + ".description";
        return this.addRenderableWidget(
            CycleButton.onOffBuilder(initial)
                .withTooltip(value -> Tooltip.create(Component.translatable(description)))
                .create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(label),
                    (button, value) -> {
                        setFlag(flag, value);
                        syncDependencies();
                    })
        );
    }

    private void setFlag(PolicyFlag flag, boolean value) {
        RescuePolicy current = this.draft.customPolicy();
        this.draft.setCustomPolicy(new RescuePolicy(
            flag == PolicyFlag.DEATH_PROTECTION ? value : current.deathProtection(),
            flag == PolicyFlag.SHIELDS ? value : current.shields(),
            flag == PolicyFlag.CONSUMABLES ? value : current.consumables(),
            flag == PolicyFlag.EQUIPMENT ? value : current.equipment(),
            flag == PolicyFlag.INVENTORY_ROUTING ? value : current.inventoryRouting(),
            flag == PolicyFlag.MAIN_HAND_TAKEOVER ? value : current.mainHandTakeover(),
            flag == PolicyFlag.PROACTIVE_DUAL_PROTECTION ? value : current.proactiveDualProtection()
        ));
    }

    private void syncDependencies() {
        RescuePolicy policy = this.draft.customPolicy();
        if (this.mainHandTakeoverButton != null) {
            this.mainHandTakeoverButton.active = policy.inventoryRouting();
        }
        if (this.proactiveDualProtectionButton != null) {
            this.proactiveDualProtectionButton.active = policy.deathProtection() && policy.inventoryRouting();
        }
    }

    private void closeToParent() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    private enum PolicyFlag {
        DEATH_PROTECTION,
        SHIELDS,
        CONSUMABLES,
        EQUIPMENT,
        INVENTORY_ROUTING,
        MAIN_HAND_TAKEOVER,
        PROACTIVE_DUAL_PROTECTION
    }
}
