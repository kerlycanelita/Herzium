package dev.zymekoh.herzium.debug.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Exposes the diagnostic viewer without coupling Herzium to Mod Menu. */
public final class HerziumDebugModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HerziumDebugScreen::new;
    }
}
