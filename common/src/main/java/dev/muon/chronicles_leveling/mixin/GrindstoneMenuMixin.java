package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Transcribe (Enchanting): when a plain book sits in the grindstone's second slot and an enchanted item in
 * the top slot, the result becomes an enchanted book carrying the item's enchantments (half/all by rank). The
 * result-slot take is handled separately in {@code GrindstoneTranscribeMixin} (rank-3 item preservation).
 *
 * <p>The menu holds no player reference, so it's captured from the inventory. {@code computeResult} runs on
 * both sides, but the perk read needs a {@code ServerPlayer}; the client falls through to vanilla (EMPTY for
 * item+book) and shows the server-computed result via slot sync.
 */
@Mixin(value = GrindstoneMenu.class, remap = false)
public abstract class GrindstoneMenuMixin {

    @Unique
    private Player chronicles_leveling$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL"), remap = false)
    private void chronicles_leveling$capturePlayer(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.chronicles_leveling$player = inventory.player;
    }

    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true, remap = false)
    private void chronicles_leveling$transcribe(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack book = EnchantingPerks.transcribeResult(this.chronicles_leveling$player, input, additional);
        if (!book.isEmpty()) {
            cir.setReturnValue(book);
        }
    }

    /** Clear the Wizard's Study "Magically Infused" bonus from any item ground down (the magic came from the enchant). */
    @ModifyReturnValue(method = "computeResult", at = @At("RETURN"), remap = false)
    private ItemStack chronicles_leveling$clearInfusion(ItemStack result) {
        return WizardsStudyHandler.stripInfusion(result);
    }
}
