package dev.adrien.crystaloptimizer.client.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.adrien.crystaloptimizer.client.config.OptimizerConfigScreen;
import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;

public final class CrystalOptimizerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new OptimizerConfigScreen(
            parent,
            OptimizerConfigService.instance()
        );
    }
}
