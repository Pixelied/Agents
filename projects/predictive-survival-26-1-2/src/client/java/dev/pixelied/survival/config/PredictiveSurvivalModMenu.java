package dev.pixelied.survival.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.pixelied.survival.PredictiveSurvivalClient;

public final class PredictiveSurvivalModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PredictiveSurvivalClient::createConfigScreen;
    }
}
