package dev.adrien.crystaloptimizer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.v2.ClientCombatCoordinator;
import dev.adrien.crystaloptimizer.client.v2.ClientCombatEventBus;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
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
    private OptimizerConfigService configService;
    private ClientCombatCoordinator coordinator;

    @Override
    public void onInitializeClient() {
        configService = OptimizerConfigService.instance();
        coordinator = ClientCombatCoordinator.create(Minecraft.getInstance(), configService);
        ClientCombatEventBus.instance().subscribe(coordinator::onEvent);
        OptimizerHud.register(coordinator::diagnostics);

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.crystaloptimizer.toggle",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_O,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                configService.apply(
                    configService.current().withEnabled(!configService.current().enabled())
                );
                ClientCombatEventBus.instance().publish(new CombatEvent.ConfigChanged(
                    configService.revision(),
                    System.nanoTime()
                ));
            }
            coordinator.tick();
        });
    }
}
