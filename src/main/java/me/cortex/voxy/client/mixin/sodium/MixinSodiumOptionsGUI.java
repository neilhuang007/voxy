package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.config.ModMenuIntegration;
import me.cortex.voxy.client.config.VoxyConfigScreenPages;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumOptionsGUI.class, remap = false)
public class MixinSodiumOptionsGUI {
    @Inject(method = "init", at = @At("TAIL"))
    private void voxy$selectPageAfterInit(CallbackInfo ci) {
        if (ModMenuIntegration.consumeOpenVoxyPageOnNextSodiumScreen()) {
            ((SodiumOptionsGUI) (Object) this).setPage(VoxyConfigScreenPages.page());
        }
    }
}
