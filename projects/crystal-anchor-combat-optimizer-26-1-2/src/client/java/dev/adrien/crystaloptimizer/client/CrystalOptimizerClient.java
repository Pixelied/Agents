package dev.adrien.crystaloptimizer.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class CrystalOptimizerClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("crystaloptimizer", "controls")
    );

    private KeyMapping toggleKey;
    private ClientCombatRuntime runtime;

    @Override
    public void onInitializeClient() {
        runtime = new ClientCombatRuntime(Minecraft.getInstance());
        OptimizerHud.register(runtime);
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.crystaloptimizer.toggle",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_O,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                runtime.setEnabled(!runtime.enabled());
            }
            runtime.tick();
        });
    }
}
