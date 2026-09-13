package net.krodark.asterion.port.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.krodark.asterion.port.client.PortAsterionSettingsScreen;

public final class PortAsterionModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PortAsterionSettingsScreen::new;
    }
}
