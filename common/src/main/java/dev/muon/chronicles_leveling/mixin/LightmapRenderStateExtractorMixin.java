package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.client.CaveEyesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cave Eyes (Mining): gives the lightmap a weak night-vision intensity while the perk is held and the player
 * is not exposed to the sky, but only when no real Night Vision / Conduit effect already brightens it.
 */
@Mixin(value = LightmapRenderStateExtractor.class, remap = false)
public class LightmapRenderStateExtractorMixin {

    @Inject(method = "extract", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$caveEyesNightVision(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (renderState.nightVisionEffectIntensity > 0f) {
            return;   // a real Night Vision / Conduit effect already applies
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            renderState.nightVisionEffectIntensity = CaveEyesClient.intensity(player);
        }
    }
}
