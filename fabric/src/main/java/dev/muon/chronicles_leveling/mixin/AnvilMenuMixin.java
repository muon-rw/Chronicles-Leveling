package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HEAD-injects so {@link AnvilMenu#getCost} still reflects the operation's
 *  cost — vanilla resets it to 0 partway through {@code onTake}. */
@Mixin(value = AnvilMenu.class, remap = false)
public abstract class AnvilMenuMixin {

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$grantAnvilXp(Player player, ItemStack carried, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AnvilMenu self = (AnvilMenu) (Object) this;
        EnchantingXpHandler.onAnvilTake(serverPlayer, self.getCost());
    }
}
