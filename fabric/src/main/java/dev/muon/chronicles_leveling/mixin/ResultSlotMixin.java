package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.SmithingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crafting-table result slot (and the 2x2 inventory grid). Smithing-table results bypass
 * {@link ResultSlot} and are handled by {@code SmithingMenuMixin} in common.
 *
 * <p>Grants at {@code checkTakeAchievements} rather than {@code onTake}: that is where the count
 * is authoritative. {@code removeCount} is the real number crafted this take (set by {@code remove}
 * on a normal click, by {@code onQuickCraft} on a shift-click), and {@code carried} here is the full
 * pre-move copy, so the stack identity and count are both intact even on shift-click (an {@code onTake}
 * hook reads a result already emptied into the inventory). This is the exact seam NeoForge fires
 * {@code PlayerEvent.ItemCraftedEvent} from, so both loaders grant identically.
 */
@Mixin(value = ResultSlot.class, remap = false)
public abstract class ResultSlotMixin {

    @Shadow private int removeCount;
    @Shadow @Final private Player player;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$grantSmithingXp(ItemStack carried, CallbackInfo ci) {
        if (this.removeCount > 0 && this.player instanceof ServerPlayer serverPlayer) {
            SmithingXpHandler.onCraft(serverPlayer, carried);
        }
    }
}
