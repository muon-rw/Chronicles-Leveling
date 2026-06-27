package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Transcribe: lets a plain book be placed in the grindstone's input slots. Vanilla's two repair slots
 * ({@code GrindstoneMenu$2} top, {@code GrindstoneMenu$3} bottom) reject anything that isn't damageable or
 * enchanted, so a book could never be inserted to receive transcribed enchantments. Allowing books is harmless
 * without the perk (a book + item produces no vanilla grindstone result), and the Transcribe result itself is
 * gated on the perk in {@code GrindstoneMenuMixin}.
 */
@Mixin(targets = {
        "net.minecraft.world.inventory.GrindstoneMenu$2",
        "net.minecraft.world.inventory.GrindstoneMenu$3"
}, remap = false)
public abstract class GrindstoneSlotMixin {

    @ModifyReturnValue(method = "mayPlace", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$allowBook(boolean original, @Local(argsOnly = true) ItemStack stack) {
        return original || stack.is(Items.BOOK);
    }
}
