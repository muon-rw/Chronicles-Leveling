package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.SmithingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smithing-table upgrade XP, both loaders (NeoForge's {@code ItemCraftedEvent} fires only for the
 * crafting-grid {@code ResultSlot}, never the smithing table, so this is the only smithing-table grant).
 *
 * <p>The result is captured when it is assembled in {@code createResult}, not read at take: on a
 * shift-click the result is moved into the inventory before {@code onTake} runs, leaving {@code carried}
 * empty (count 0, components stripped). The captured copy is the full assembled output, so the tier and
 * count are intact however the player takes it.
 */
@Mixin(value = SmithingMenu.class, remap = false)
public abstract class SmithingMenuMixin {

    @Unique private ItemStack chronicles_leveling$lastResult = ItemStack.EMPTY;

    @Inject(method = "createResult", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$captureResult(CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        this.chronicles_leveling$lastResult = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
    }

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$grantSmithingXp(Player player, ItemStack carried, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack crafted = carried.isEmpty() ? this.chronicles_leveling$lastResult : carried;
            SmithingXpHandler.onCraft(serverPlayer, crafted);
        }
    }
}
