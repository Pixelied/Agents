package studio.pixelied.pearlcatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * Public Pearl Catcher facade.
 *
 * Runtime execution, attempt bookkeeping, and debug tooling live in focused package-private
 * components so this class stays limited to the Fabric-facing API surface.
 */
public final class PearlCatchMode {
    private final CatchCoordinator coordinator = new CatchCoordinator();

    public void beginClientTick(Minecraft mc) {
        coordinator.beginClientTick(mc);
    }

    public void captureEndClientTick(Minecraft mc) {
        coordinator.captureEndClientTick(mc);
    }

    public void tick(Minecraft mc, PearlCatchConfig config) {
        coordinator.tick(mc, config);
    }

    public void triggerAutoPearlCatch(Minecraft mc, PearlCatchConfig config) {
        coordinator.triggerAutoPearlCatch(mc, config);
    }

    public void triggerVerticalPearlCatch(Minecraft mc, PearlCatchConfig config) {
        coordinator.triggerVerticalPearlCatch(mc, config);
    }

    public void toggleDebugSweep(Minecraft mc, PearlCatchConfig config) {
        coordinator.toggleDebugSweep(mc, config);
    }

    public void onEntityLoaded(Minecraft mc, Entity entity, ClientLevel level, PearlCatchConfig config) {
        coordinator.onEntityLoaded(mc, entity, level, config);
    }
}
