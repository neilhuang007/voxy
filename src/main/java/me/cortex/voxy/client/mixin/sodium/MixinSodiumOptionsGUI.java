package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.config.ModMenuIntegration;
import me.cortex.voxy.client.config.VoxyConfigScreenPages;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = SodiumOptionsGUI.class, remap = false)
public class MixinSodiumOptionsGUI {
    @Shadow @Final private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$addVoxyPage(CallbackInfo ci) {
        if (!VoxyCommon.isAvailable()) {
            return;
        }

        var voxyPage = VoxyConfigScreenPages.page();
        if (!this.pages.contains(voxyPage)) {
            this.pages.add(voxyPage);
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void voxy$selectPageAfterInit(CallbackInfo ci) {
        if (ModMenuIntegration.consumeOpenVoxyPageOnNextSodiumScreen()) {
            ((SodiumOptionsGUI) (Object) this).setPage(VoxyConfigScreenPages.page());
        }
    }
}
