package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.client.enchant.WizardsStudyClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wizard's Study glow: forces the entity-outline pass on when the player's most-used table is in the rendered
 * dimension. Vanilla only allocates the outline target + composite when a glowing <i>entity</i> is in view
 * ({@code haveGlowingEntities}); the table glow is block geometry ({@link EnchantTableRendererMixin}), so the
 * flag must be set for a block-only outline to show. The flag is public and reset each frame, then written during
 * extraction before the frame graph reads it; this OR-s our condition in at the end of block-entity extraction.
 *
 * <p>Scoped to frames where the target table is actually being rendered (its render state is in this frame's
 * {@code blockEntityRenderStates}), so the full-screen outline pass isn't forced while the table is off-screen.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public class LevelRendererMixin {

    @Inject(method = "extractVisibleBlockEntities", at = @At("TAIL"), remap = false, require = 0)
    private void chronicles_leveling$forceTableGlow(Camera camera, float deltaPartialTick, LevelRenderState levelRenderState, CallbackInfo ci) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !WizardsStudyClient.hasTargetIn(level.dimension())) {
            return;
        }
        for (BlockEntityRenderState state : levelRenderState.blockEntityRenderStates) {
            if (WizardsStudyClient.matches(state.blockPos, level.dimension())) {
                levelRenderState.haveGlowingEntities = true;
                return;
            }
        }
    }
}
