package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.SmithingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SmithingMenu.class, remap = false)
public abstract class SmithingMenuMixin {

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$grantSmithingXp(Player player, ItemStack carried, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            SmithingXpHandler.onCraft(serverPlayer, carried);
        }
    }
}
