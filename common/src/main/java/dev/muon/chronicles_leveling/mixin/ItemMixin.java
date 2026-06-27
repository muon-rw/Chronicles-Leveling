package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.item.StackablePotions;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.alchemy.PotionCooldown;
import dev.muon.chronicles_leveling.skill.catalog.MiningSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Item.class, remap = false)
public abstract class ItemMixin {

    /**
     * Applies the anti-spam drink cooldown when a player finishes drinking a potion. Drinkable potions consume through the
     * base {@code Item.finishUsingItem} (the CONSUMABLE component, not a PotionItem override), so this filters that shared
     * seam to potion items; thrown potions never reach it. Bypassed by the Deft Hands perk; see {@link PotionCooldown}.
     */
    @Inject(method = "finishUsingItem", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$drinkCooldown(ItemStack itemStack, Level level, LivingEntity entity,
            CallbackInfoReturnable<ItemStack> cir) {
        if (entity instanceof ServerPlayer serverPlayer && StackablePotions.isPotion(itemStack.getItem())) {
            PotionCooldown.applyDrink(serverPlayer, itemStack);
        }
    }

    /**
     * Mining "Sturdy Tools": a fraction of mining-tool durability damage avoided. Wraps the
     * {@code ItemStack#hurtAndBreak} call inside {@code Item#mineBlock} (the block-break durability path
     * specifically; combat/attack durability runs elsewhere, so this stays mining-scoped), giving a player with
     * the perk a chance to take no wear on a given break. {@code remap = false} (Mojmap fork).
     */
    @WrapOperation(
            method = "mineBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"),
            remap = false)
    private void chronicles_leveling$miningDurabilitySave(ItemStack stack, int amount, LivingEntity owner,
                                                          EquipmentSlot slot, Operation<Void> original) {
        if (owner instanceof ServerPlayer player) {
            double save = SkillEffects.get(player, MiningSkill.TOOL_DURABILITY_SAVE);
            if (save > 0 && player.getRandom().nextDouble() < Math.min(save, 1.0)) {
                return;   // skip this break's durability damage entirely
            }
        }
        original.call(stack, amount, owner, slot);
    }
}
