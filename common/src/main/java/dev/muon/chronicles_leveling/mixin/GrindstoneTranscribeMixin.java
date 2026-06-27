package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Transcribe rank 3: preserves the source item when the transcribed book is taken. The grindstone result
 * slot ({@code GrindstoneMenu$4}) clears its inputs at the end of {@code onTake} via
 * {@code repairSlots.setItem(0, EMPTY)}; that call's receiver is the input container, so wrapping it (slot 0
 * only) gives the inputs and, via {@code @Local}, the taking player, with no need for the menu's outer
 * reference. The handler hands the stripped item back before the slot is cleared, then clears as vanilla does.
 *
 * <p>Common (both loaders): the result-slot behavior is vanilla. Targeted by the same {@code $4} anchor the XP
 * mixin uses; if MC reorders the constructor's anonymous slots, only this string follows.
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4", remap = false)
public abstract class GrindstoneTranscribeMixin {

    @WrapOperation(method = "onTake",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V",
                    ordinal = 0),
            remap = false)
    private void chronicles_leveling$preserveSource(Container repairSlots, int index, ItemStack value,
                                                    Operation<Void> original, @Local(argsOnly = true) Player player) {
        EnchantingPerks.preserveTranscribedItem(player, repairSlots);
        original.call(repairSlots, index, value);
    }

    /**
     * Transcribe: suppress the disenchant XP. A Transcribe op (a plain book in the second slot) transfers the
     * enchantments to the book instead of grinding them off, so it shouldn't award the usual orbs. Zeroing the
     * slot-0 contribution to {@code getExperienceAmount} drops the XP to 0 (the book in slot 1 has none anyway).
     */
    @WrapOperation(method = "getExperienceAmount",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/Container;getItem(I)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 0),
            remap = false)
    private ItemStack chronicles_leveling$noTranscribeXp(Container repairSlots, int index, Operation<ItemStack> original) {
        return repairSlots.getItem(1).is(Items.BOOK) ? ItemStack.EMPTY : original.call(repairSlots, index);
    }
}
