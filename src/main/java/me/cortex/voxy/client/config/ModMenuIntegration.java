package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.screen.ConfigCorruptedScreen;

public class ModMenuIntegration implements ModMenuApi {
    private static volatile boolean openVoxyPageOnNextSodiumScreen;

    public static boolean consumeOpenVoxyPageOnNextSodiumScreen() {
        boolean open = openVoxyPageOnNextSodiumScreen;
        openVoxyPageOnNextSodiumScreen = false;
        return open;
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!VoxyCommon.isAvailable()) {
                return null;
            }

            if (SodiumClientMod.options().isReadOnly()) {
                return new ConfigCorruptedScreen(parent, SodiumOptionsGUI::createScreen);
            }

            openVoxyPageOnNextSodiumScreen = true;
            return SodiumOptionsGUI.createScreen(parent);
        };
    }
}
