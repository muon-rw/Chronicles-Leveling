package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.fishing.FishingHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Worthy rank 3: keeps an auto-returning trident from being discarded in the void, so it survives long enough to return. */
@Mixin(value = Entity.class, remap = false)
public abstract class EntityMixin {

    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void chronicles_leveling$voidProofTrident(CallbackInfo ci) {
        if ((Object) this instanceof ThrownTrident trident
                && trident.getOwner() instanceof Player player
                && FishingHooks.isTridentAutoReturn(player)) {
            ci.cancel();
        }
    }
}
