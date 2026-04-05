package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.screen.ConfigCorruptedScreen;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!VoxyCommon.isAvailable()) {
                return null;
            }

            Screen screen = SodiumClientMod.options().isReadOnly()
                    ? new ConfigCorruptedScreen(parent, SodiumOptionsGUI::createScreen)
                    : SodiumOptionsGUI.createScreen(parent);
            try {
                ((SodiumOptionsGUI) screen).setPage(VoxyConfigScreenPages.page());
            } catch (Exception e) {
                Logger.error("Failed to set the current page to voxy", e);
            }
            return screen;
        };
    }
}
