package dev.zymekoh.herzium.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.zymekoh.herzium.gui.HerziumConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HerziumConfigScreen::new;
    }
}
