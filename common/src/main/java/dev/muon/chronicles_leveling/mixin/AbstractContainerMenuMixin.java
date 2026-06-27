package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.gather.FurnaceOwnerStore;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the player who loads a brewing stand's ingredient as its {@code owner} on the
 * {@link dev.muon.chronicles_leveling.skill.xp.BrewingStationData} attachment. {@code doBrew} is a static, playerless
 * tick, so the alchemy brewing perks need an attributed player; the most-recent ingredient-inserter is that player
 * (the catalyst of the brew). {@code clicked} carries the {@link Player}; a {@code @Share} flag brackets the click to
 * detect the ingredient slot going empty to filled. Server-side write; gated to a brewing-stand menu.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Unique private static final int chronicles_leveling$INGREDIENT_SLOT = 3;

    @Inject(method = "clicked", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$snapshotIngredient(int slotIndex, int buttonNum, ContainerInput containerInput, Player player,
            CallbackInfo ci, @Share("ingredientWasEmpty") LocalRef<Boolean> wasEmpty) {
        if ((Object) this instanceof BrewingStandMenu) {
            wasEmpty.set(chronicles_leveling$ingredientSlot().getItem().isEmpty());
        }
    }

    @Inject(method = "clicked", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$captureBrewer(int slotIndex, int buttonNum, ContainerInput containerInput, Player player,
            CallbackInfo ci, @Share("ingredientWasEmpty") LocalRef<Boolean> wasEmpty) {
        // wasEmpty is only TRUE when HEAD saw a BrewingStandMenu, so no need to re-check the menu type here.
        if (!Boolean.TRUE.equals(wasEmpty.get()) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Slot ingredient = chronicles_leveling$ingredientSlot();
        if (ingredient.getItem().isEmpty() || !(ingredient.container instanceof BrewingStandBlockEntity be)
                || be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        BrewingStationStore store = Services.PLATFORM.getBrewingStationStore();
        store.set(be, store.get(be).withOwner(serverPlayer.getUUID()));
    }

    @Unique
    private Slot chronicles_leveling$ingredientSlot() {
        return ((AbstractContainerMenu) (Object) this).getSlot(chronicles_leveling$INGREDIENT_SLOT);
    }

    // Furnace cook attribution (Gardener's Infusion): the player who loads the input is credited when the smelt
    // assembles its result; symmetric to the brewing capture above, gated to a furnace/smoker/blast menu.

    @Inject(method = "clicked", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$snapshotFurnaceInput(int slotIndex, int buttonNum, ContainerInput containerInput, Player player,
            CallbackInfo ci, @Share("furnaceInputWasEmpty") LocalRef<Boolean> wasEmpty) {
        if ((Object) this instanceof AbstractFurnaceMenu) {
            wasEmpty.set(chronicles_leveling$furnaceInputSlot().getItem().isEmpty());
        }
    }

    @Inject(method = "clicked", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$captureFurnaceCook(int slotIndex, int buttonNum, ContainerInput containerInput, Player player,
            CallbackInfo ci, @Share("furnaceInputWasEmpty") LocalRef<Boolean> wasEmpty) {
        if (!Boolean.TRUE.equals(wasEmpty.get()) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Slot input = chronicles_leveling$furnaceInputSlot();
        if (input.getItem().isEmpty() || !(input.container instanceof AbstractFurnaceBlockEntity be)
                || be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        FurnaceOwnerStore store = Services.PLATFORM.getFurnaceOwnerStore();
        store.set(be, store.get(be).withOwner(serverPlayer.getUUID()));
    }

    @Unique
    private Slot chronicles_leveling$furnaceInputSlot() {
        return ((AbstractContainerMenu) (Object) this).getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT);
    }
}
