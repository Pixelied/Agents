package gg.vape.fabric;

import gg.vape.Vape;
import gg.vape.module.render.ESP;
import gg.vape.utils.MutableColor;
import gg.vape.wrapper.impl.EntityPlayerSP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/** Bridges recovered ESP targeting/color rules into 26.2 entity render state. */
public final class FabricOutlineHooks {
    private FabricOutlineHooks() {
    }

    public static boolean apply(Entity entity, EntityRenderState state) {
        if (!FabricMigrationRuntime.isCoreStarted() || entity == null || state == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || Vape.INSTANCE == null || Vape.INSTANCE.getModManager() == null) {
            return false;
        }
        ESP esp = Vape.INSTANCE.getModManager().getMod(ESP.class);
        if (esp == null || !esp.isEnabled() || !esp.isOutlineModeActive()) {
            return false;
        }
        MutableColor color = esp.resolveOutlineColor(new EntityPlayerSP(minecraft.player), entity);
        if (color == null) {
            return false;
        }
        state.outlineColor = 0xFF000000
                | (color.getRed() << 16)
                | (color.getGreen() << 8)
                | color.getBlue();
        return true;
    }
}
