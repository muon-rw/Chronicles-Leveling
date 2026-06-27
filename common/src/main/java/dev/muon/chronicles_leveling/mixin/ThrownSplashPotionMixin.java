package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.component.BrewPotency;
import dev.muon.chronicles_leveling.component.ModComponents;
import dev.muon.chronicles_leveling.skill.alchemy.PotionPerks;
import dev.muon.chronicles_leveling.skill.alchemy.ToxicologistSpread;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Splash potion impact. Substitutes the transient boosted effects (BrewPotency off the stack) so the area application
 * (instant + non-instant, with distance falloff) uses the boosted amplifier; the brew's duration scale is read
 * separately and untouched. Empowered Splash widens the gather box and stretches the falloff range. Instant harmful
 * applications are stamped for the thrower's Toxicologist kill spread, since they leave no active effect on a corpse.
 * Dispenser-thrown splashes hit the same method but have no owning player, so the player perks no-op.
 */
@Mixin(value = ThrownSplashPotion.class, remap = false)
public abstract class ThrownSplashPotionMixin {

    @ModifyExpressionValue(method = "onHitAsPotion",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/alchemy/PotionContents;getAllEffects()Ljava/lang/Iterable;"))
    private Iterable<MobEffectInstance> chronicles_leveling$boostSplash(Iterable<MobEffectInstance> original,
            @Local(argsOnly = true, name = "potionItem") ItemStack potionItem) {
        BrewPotency potency = potionItem.getOrDefault(ModComponents.BREW_POTENCY, BrewPotency.NONE);
        if (potency.isEmpty()) {
            return original;
        }
        PotionContents base = potionItem.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return BrewPotency.boosted(base, potency).getAllEffects();
    }

    @WrapOperation(method = "onHitAsPotion",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"))
    private void chronicles_leveling$stampInstantHarm(MobEffect self, ServerLevel level, Entity source, Entity owner,
            LivingEntity mob, int amplification, double scale, Operation<Void> original) {
        if (owner instanceof ServerPlayer && self.getCategory() == MobEffectCategory.HARMFUL) {
            ToxicologistSpread.recordInstantHarm(mob, self, amplification);
        }
        original.call(self, level, source, owner, mob, amplification, scale);
    }

    @ModifyExpressionValue(method = "onHitAsPotion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB chronicles_leveling$empowerSplashArea(AABB original) {
        double bonus = PotionPerks.empoweredSplash(((Projectile) (Object) this).getOwner());
        return bonus > 0.0 ? original.inflate(4.0 * bonus, 2.0 * bonus, 4.0 * bonus) : original;
    }

    @ModifyExpressionValue(method = "onHitAsPotion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/AABB;)D"))
    private double chronicles_leveling$empowerSplashFalloff(double original) {
        double bonus = PotionPerks.empoweredSplash(((Projectile) (Object) this).getOwner());
        return bonus > 0.0 ? original / ((1.0 + bonus) * (1.0 + bonus)) : original;
    }
}
