package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.SmithingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crafting-table result slot. Smithing-table results bypass {@link ResultSlot}
 *  and need {@link SmithingMenuMixin}. */
@Mixin(value = ResultSlot.class, remap = false)
public abstract class ResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$grantSmithingXp(Player player, ItemStack carried, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            SmithingXpHandler.onCraft(serverPlayer, carried);
        }
    }
}
