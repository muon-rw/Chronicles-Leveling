package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anvil XP grant, plus Experimenter (Enchanting): relaxes the anvil's enchantment-exclusivity check so the perk
 * can stack same-group damage / protection enchantments. On Fabric the result logic lives in {@code createResult};
 * the NeoForge twin targets {@code createResultInternal}. {@code extends ItemCombinerMenu} is compiler-only (to
 * reach the inherited {@code player}); at runtime the mixin merges into {@link AnvilMenu}.
 */
@Mixin(value = AnvilMenu.class, remap = false)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    public AnvilMenuMixin(@Nullable MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition itemInputSlots) {
        super(menuType, containerId, inventory, access, itemInputSlots);
    }

    /** HEAD-injects so {@link AnvilMenu#getCost} still reflects the operation's cost; vanilla resets it to 0 partway through {@code onTake}. */
    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$grantAnvilXp(Player player, ItemStack carried, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AnvilMenu self = (AnvilMenu) (Object) this;
        EnchantingXpHandler.onAnvilTake(serverPlayer, self.getCost());
    }

    /** Experimenter: treat same-group damage/protection enchants as compatible at the anvil (exclusivity only). */
    @WrapOperation(method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"),
            remap = false)
    private boolean chronicles_leveling$experimenterCompat(Holder<Enchantment> first, Holder<Enchantment> second, Operation<Boolean> original) {
        return original.call(first, second) || EnchantingPerks.canCombineExclusive(this.player, first, second);
    }
}
