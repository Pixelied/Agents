package dev.adrien.naturalaim.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.adrien.naturalaim.config.NaturalAimConfigScreen;

public final class NaturalAimModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NaturalAimConfigScreen::new;
    }
}
