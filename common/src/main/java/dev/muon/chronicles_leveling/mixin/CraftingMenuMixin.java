package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.gather.GardenersInfusionHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gardener's Infusion: stamp crafted food the moment the crafting result is assembled, so the boost lands no matter how
 * the result is taken. Hooking the assembly (this static is shared by the crafting table and the 2x2 inventory grid)
 * rather than {@code ResultSlot.onTake} is what fixes shift-click: a quick-move empties the result into the inventory
 * before onTake runs, so the on-take seam stamps a now-empty stack. The stamp is server-side and syncs with the result.
 */
@Mixin(value = CraftingMenu.class, remap = false)
public abstract class CraftingMenuMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"), remap = false)
    private static void chronicles_leveling$infuseCraftedFood(AbstractContainerMenu menu, ServerLevel level, Player player,
            CraftingContainer container, ResultContainer resultSlots, RecipeHolder<CraftingRecipe> recipeHint, CallbackInfo ci) {
        if (player instanceof ServerPlayer crafter) {
            GardenersInfusionHandler.infuse(crafter, resultSlots.getItem(0));
        }
    }
}
