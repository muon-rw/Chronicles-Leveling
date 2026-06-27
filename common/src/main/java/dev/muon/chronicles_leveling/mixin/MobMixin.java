package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.effect.ModEffects;
import dev.muon.chronicles_leveling.skill.social.SpeechTamingHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Speech mob hooks: Kindred Fury bonus pet damage, and Pacify denying a pacified mob any target. */
@Mixin(value = Mob.class, remap = false)
public abstract class MobMixin {

    @ModifyExpressionValue(method = "doHurtTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getAttributeValue(Lnet/minecraft/core/Holder;)D"),
            remap = false)
    private double chronicles_leveling$kindredFury(double damage) {
        return damage * SpeechTamingHandler.petKindredFuryMultiplier((Mob) (Object) this);
    }

    @ModifyReturnValue(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$pacifyCanAttack(boolean original) {
        if (original && ModEffects.PACIFIED != null && ((Mob) (Object) this).hasEffect(ModEffects.PACIFIED)) {
            return false;
        }
        return original;
    }

    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true, remap = false)
    private LivingEntity chronicles_leveling$pacifyTarget(LivingEntity target) {
        if (target != null && ModEffects.PACIFIED != null && ((Mob) (Object) this).hasEffect(ModEffects.PACIFIED)) {
            return null;
        }
        return target;
    }
}
