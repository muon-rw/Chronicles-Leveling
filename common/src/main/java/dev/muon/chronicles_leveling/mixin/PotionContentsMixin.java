package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.component.BrewPotency;
import dev.muon.chronicles_leveling.component.ModComponents;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * BrewPotency amplifier boost applied off the holding stack, leaving the stored item untouched:
 * <ul>
 *   <li>Drinking: substitutes transient amplifier-boosted contents into {@code onConsume}, routing both
 *       non-instant and instant effects through the boost; the unchanged {@code scale} arg preserves duration.</li>
 *   <li>Tooltip: feeds the boosted effects to {@code addToTooltip} so the potion (and tipped-arrow) line shows the
 *       boosted amplifier; duration scale is read separately, so duration display is unchanged.</li>
 * </ul>
 */
@Mixin(value = PotionContents.class, remap = false)
public abstract class PotionContentsMixin {

    @WrapOperation(method = "onConsume(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/component/Consumable;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/alchemy/PotionContents;applyToLivingEntity(Lnet/minecraft/world/entity/LivingEntity;F)V"))
    private void chronicles_leveling$boostDrink(PotionContents self, LivingEntity entity, float durationScale,
            Operation<Void> original, @Local(argsOnly = true, name = "stack") ItemStack stack) {
        BrewPotency potency = stack.getOrDefault(ModComponents.BREW_POTENCY, BrewPotency.NONE);
        if (potency.isEmpty()) {
            original.call(self, entity, durationScale);
            return;
        }
        BrewPotency.boosted(self, potency).applyToLivingEntity(entity, durationScale);
    }

    @ModifyExpressionValue(method = "addToTooltip(Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;Lnet/minecraft/core/component/DataComponentGetter;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/alchemy/PotionContents;getAllEffects()Ljava/lang/Iterable;"))
    private Iterable<MobEffectInstance> chronicles_leveling$boostTooltip(Iterable<MobEffectInstance> original,
            @Local(argsOnly = true, name = "components") DataComponentGetter components) {
        BrewPotency potency = components.get(ModComponents.BREW_POTENCY);
        if (potency == null || potency.isEmpty()) {
            return original;
        }
        return BrewPotency.boosted((PotionContents) (Object) this, potency).getAllEffects();
    }
}
