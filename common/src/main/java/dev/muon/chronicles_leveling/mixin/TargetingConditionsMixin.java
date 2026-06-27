package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.effect.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pacify (Speech): a pacified mob cannot pass any targeting check, so it drops its target and acquires none. */
@Mixin(value = TargetingConditions.class, remap = false)
public abstract class TargetingConditionsMixin {

    @ModifyReturnValue(
            method = "test(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$pacify(boolean original, ServerLevel level, LivingEntity targeter, LivingEntity target) {
        if (original && targeter != null && ModEffects.PACIFIED != null && targeter.hasEffect(ModEffects.PACIFIED)) {
            return false;
        }
        return original;
    }
}
