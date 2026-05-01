package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code clickMenuButton} returns true only on a successful enchant; the
 *  slot's {@code costs[]} entry is the level cost vanilla used to roll the
 *  enchantments, which already folds in the bookshelf count via
 *  {@code EnchantmentHelper.getEnchantmentCost}. NeoForge's
 *  {@code PlayerEnchantItemEvent} doesn't expose this cost, hence the mixin. */
@Mixin(value = EnchantmentMenu.class, remap = false)
public abstract class EnchantmentMenuMixin {

    @Shadow @Final
    public int[] costs;

    @Inject(method = "clickMenuButton", at = @At("RETURN"), remap = false)
    private void chronicles_leveling$grantEnchantingXp(
            Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (buttonId < 0 || buttonId >= costs.length) return;
        int levelCost = costs[buttonId];
        if (levelCost <= 0) return;
        EnchantingXpHandler.onTableEnchant(serverPlayer, levelCost);
    }
}
