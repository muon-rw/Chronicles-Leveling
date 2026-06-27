package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.gather.GardenersInfusionHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/**
 * Gardener's Infusion: bake the boost into a smelted food at the moment its result is assembled, crediting the cook
 * tracked on the furnace owner attachment (set when the player loaded the input). Stamping here, before {@code canBurn}
 * checks the result against the output slot, lets successive same-cook results accumulate (an already-infused output
 * only accepts more identically-infused items); it also fires regardless of how the output is later collected, so
 * hopper-drained furnaces are covered too. {@code remap = false} (Mojmap fork).
 */
@Mixin(value = AbstractFurnaceBlockEntity.class, remap = false)
public abstract class AbstractFurnaceBlockEntityMixin {

    @ModifyExpressionValue(method = "serverTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;"),
            remap = false)
    private static ItemStack chronicles_leveling$infuseSmeltedFood(ItemStack burnResult,
            @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) AbstractFurnaceBlockEntity entity) {
        if (burnResult.isEmpty() || !burnResult.has(DataComponents.FOOD)) {
            return burnResult;
        }
        UUID owner = Services.PLATFORM.getFurnaceOwnerStore().get(entity).ownerOrNull();
        if (owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer crafter) {
            GardenersInfusionHandler.infuse(crafter, burnResult);
        }
        return burnResult;
    }
}
