package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.client.enchant.WizardsStudyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Wizard's Study glow: gives the most-used table's floating book a nonzero outline color so the entity-outline
 * post-pass draws a colored silhouette around it (the same mechanism as a glowing entity, and the only one that
 * adds an outline without re-drawing the block: the table base is chunk-rendered terrain, but the book is the
 * renderer's own submit). {@link LevelRendererMixin} forces that pass on. Glows only for the owning client (the
 * target is synced only to that player).
 */
@Mixin(value = EnchantTableRenderer.class, remap = false)
public class EnchantTableRendererMixin {

    @Unique
    private static final int chronicles_leveling$GLOW = 0xFFFF55FF;   // opaque light purple, matches "Magically Infused"

    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            index = 8,
            remap = false,
            require = 0)
    private int chronicles_leveling$glowMostUsedBook(int outlineColor, @Local(argsOnly = true) EnchantTableRenderState state) {
        Level level = Minecraft.getInstance().level;
        if (level != null && WizardsStudyClient.matches(state.blockPos, level.dimension())) {
            return chronicles_leveling$GLOW;
        }
        return outlineColor;
    }
}
