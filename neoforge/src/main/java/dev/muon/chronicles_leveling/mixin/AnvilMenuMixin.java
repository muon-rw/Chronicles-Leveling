package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Experimenter (Enchanting) on NeoForge; NeoForge splits the anvil result into {@code createResultInternal()}
 * (the vanilla logic, wrapped by {@code createResult()} which fires {@code AnvilUpdateEvent}), so the
 * enchant-compatibility call lives there, not in {@code createResult} (the Fabric/vanilla name). Same hook as
 * the Fabric {@code AnvilMenuMixin}, routed through {@code EnchantingPerks}; relaxes only enchantment exclusivity
 * (not applicability), gated by the perk. Anvil XP is granted via an event on NeoForge, so it isn't here.
 */
@Mixin(value = AnvilMenu.class, remap = false)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    public AnvilMenuMixin(@Nullable MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition itemInputSlots) {
        super(menuType, containerId, inventory, access, itemInputSlots);
    }

    @WrapOperation(method = "createResultInternal",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"),
            remap = false)
    private boolean chronicles_leveling$experimenterCompat(Holder<Enchantment> first, Holder<Enchantment> second, Operation<Boolean> original) {
        return original.call(first, second) || EnchantingPerks.canCombineExclusive(this.player, first, second);
    }
}
