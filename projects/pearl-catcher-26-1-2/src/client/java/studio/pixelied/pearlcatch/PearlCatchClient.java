package studio.pixelied.pearlcatch;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PearlCatchClient implements ClientModInitializer {
    public static final String MOD_ID = "pearlcatch";
    public static final PearlCatchConfig CONFIG = PearlCatchConfig.load();

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );

    private final PearlCatchMode mode = new PearlCatchMode();

    private static final KeyMapping autoCatchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.pearlcatch.auto_catch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY
    ));

    private static final KeyMapping verticalCatchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.pearlcatch.vertical_catch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY
    ));

    private static final KeyMapping debugSweepKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.pearlcatch.debug_sweep", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY
    ));

    static void drainSyntheticControlEchoes(InputConstants.Key syntheticKey) {
        drainIfBoundTo(autoCatchKey, syntheticKey);
        drainIfBoundTo(verticalCatchKey, syntheticKey);
        drainIfBoundTo(debugSweepKey, syntheticKey);
    }

    private static void drainIfBoundTo(KeyMapping mapping, InputConstants.Key syntheticKey) {
        if (mapping == null || syntheticKey == null) return;
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(mapping);
        if (!syntheticKey.equals(bound)) return;
        while (mapping.consumeClick()) {
            // Remove only the echo created after this turn's real G/H/B clicks were already consumed.
        }
    }

    @Override
    public void onInitializeClient() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) ->
                mode.onEntityLoaded(Minecraft.getInstance(), entity, level, CONFIG));

        ClientTickEvents.START_CLIENT_TICK.register(mode::beginClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            mode.captureEndClientTick(client);
            while (debugSweepKey.consumeClick()) mode.toggleDebugSweep(client, CONFIG);
            while (verticalCatchKey.consumeClick()) mode.triggerVerticalPearlCatch(client, CONFIG);
            while (autoCatchKey.consumeClick()) mode.triggerAutoPearlCatch(client, CONFIG);
            mode.tick(client, CONFIG);
        });
    }
}
