package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.component.BrewPotency;
import dev.muon.chronicles_leveling.component.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ImbueRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tipped-arrow crafting (arrows around a lingering potion): vanilla copies only the potion's {@code POTION_CONTENTS}
 * onto the result, so this also carries the brewed potion's BrewPotency and {@code POTION_DURATION_SCALE} onto the
 * arrow, letting an empowered lingering's amplifier and duration ride into the tipped arrow.
 */
@Mixin(value = ImbueRecipe.class, remap = false)
public abstract class ImbueRecipeMixin {

    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private void chronicles_leveling$carryPotencyToArrow(CraftingInput input, CallbackInfoReturnable<ItemStack> cir,
            @Local(name = "source") ItemStack source) {
        BrewPotency potency = source.get(ModComponents.BREW_POTENCY);
        if (potency != null && !potency.isEmpty()) {
            cir.getReturnValue().set(ModComponents.BREW_POTENCY, potency);
        }
        Float scale = source.get(DataComponents.POTION_DURATION_SCALE);
        if (scale != null) {
            cir.getReturnValue().set(DataComponents.POTION_DURATION_SCALE, scale);
        }
    }
}
