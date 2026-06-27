package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.muon.chronicles_leveling.component.BrewPotency;
import dev.muon.chronicles_leveling.component.ModComponents;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tipped-arrow on-hit: substitutes the transient boosted contents so the applied effects use the boosted amplifier off
 * the arrow's carried BrewPotency (rides on the pickup-origin stack). Duration scale is applied separately by
 * {@code forEachEffect}, so this is amplifier-only. Targets {@link Arrow} (not the shared {@code AbstractArrowMixin})
 * because {@code doPostHurtEffects} is overridden here.
 */
@Mixin(value = Arrow.class, remap = false)
public abstract class ArrowMixin {

    @ModifyExpressionValue(method = "doPostHurtEffects",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/arrow/Arrow;getPotionContents()Lnet/minecraft/world/item/alchemy/PotionContents;"))
    private PotionContents chronicles_leveling$boostArrowApply(PotionContents original) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        BrewPotency potency = self.getPickupItemStackOrigin().get(ModComponents.BREW_POTENCY);
        return BrewPotency.boosted(original, potency);
    }
}
