package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.component.BrewPotency;
import dev.muon.chronicles_leveling.component.ModComponents;
import dev.muon.chronicles_leveling.skill.alchemy.PotionPerks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lingering potion impact. After vanilla copies the base contents + duration scale onto the cloud, overwrites the
 * cloud's potion contents with the transient boosted set (BrewPotency off the stack). The cloud is transient and has
 * no base identity to preserve, and the source bottle is never written; the cloud's per-tick application then uses
 * the boosted amplifier with the copied duration scale intact. Empowered Splash scales the cloud radius.
 */
@Mixin(value = ThrownLingeringPotion.class, remap = false)
public abstract class ThrownLingeringPotionMixin {

    @Inject(method = "onHitAsPotion",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/world/entity/AreaEffectCloud;applyComponentsFromItemStack(Lnet/minecraft/world/item/ItemStack;)V"))
    private void chronicles_leveling$boostLingeringCloud(ServerLevel level, ItemStack potionItem, HitResult hitResult,
            CallbackInfo ci, @Local(name = "cloud") AreaEffectCloud cloud) {
        BrewPotency potency = potionItem.getOrDefault(ModComponents.BREW_POTENCY, BrewPotency.NONE);
        if (potency.isEmpty()) {
            return;
        }
        PotionContents base = potionItem.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        cloud.setPotionContents(BrewPotency.boosted(base, potency));
    }

    @ModifyArg(method = "onHitAsPotion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/AreaEffectCloud;setRadius(F)V"))
    private float chronicles_leveling$empowerCloudRadius(float radius) {
        double bonus = PotionPerks.empoweredSplash(((Projectile) (Object) this).getOwner());
        return bonus > 0.0 ? (float) (radius * (1.0 + bonus)) : radius;
    }
}
