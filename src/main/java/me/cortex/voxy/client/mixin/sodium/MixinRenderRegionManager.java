package me.cortex.voxy.client.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = RenderRegionManager.class, remap = false)
public class MixinRenderRegionManager {
    // Sodium 0.6.13 no longer routes region uploads through the previous fade conversion
    // path, so there is no 1.21.1 equivalent call site to intercept here.
}
