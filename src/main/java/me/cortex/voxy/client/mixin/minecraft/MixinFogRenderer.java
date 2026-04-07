package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.util.CapturedFogState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogRenderer.class, priority = 900)
public class MixinFogRenderer {
    private static final float SUPPRESSED_FOG_DISTANCE = 99999999.0f;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void voxy$modifyFog(Camera camera, FogRenderer.FogMode fogMode, float renderDistance, boolean thickFog, float partialTick, CallbackInfo ci) {
        if (fogMode != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }

        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        float[] fogColor = RenderSystem.getShaderFogColor();
        CapturedFogState.capture(fogStart, fogEnd, fogColor[0], fogColor[1], fogColor[2], fogColor[3]);

        if (!VoxyConfig.CONFIG.isRenderingEnabled()) {
            return;
        }
        if (IGetVoxyRenderSystem.getNullable() == null) {
            return;
        }
        if (!CapturedFogState.shouldSuppressFog(renderDistance, fogEnd, VoxyConfig.CONFIG.useEnvironmentalFog)) {
            return;
        }

        if (CapturedFogState.shouldTagBorderDistanceFog(renderDistance, fogEnd)) {
            CapturedFogState.tagBorderDistanceFog();
        }

        RenderSystem.setShaderFogStart(SUPPRESSED_FOG_DISTANCE);
        RenderSystem.setShaderFogEnd(SUPPRESSED_FOG_DISTANCE);
    }
}
