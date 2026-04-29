package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.client.PlayerNameplateRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric equivalent of NeoForge's {@code RenderNameTagEvent} for player
 * nameplate decoration. Mirrors Dynamic-Difficulty's player-side hook so the
 * two mods can coexist (DD skips player injection when CL is loaded).
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
    @Nullable
    private Component chronicles_leveling$decoratePlayerNameTag(@Nullable Component displayName, Entity entity) {
        if (displayName == null || !(entity instanceof Player player)) return displayName;
        return PlayerNameplateRenderer.decorate(displayName, player);
    }
}
