package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.effect.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pacify (Speech): the Warden bypasses canAttack entirely; canTargetEntity is its single validity gate, consulted
 * by anger growth, roar-target acquisition, the sonic-boom re-filter, and fight retention. Denying it while pacified
 * starves new anger, blocks the roar-to-attack promotion, and clears the active fight target.
 */
@Mixin(value = Warden.class, remap = false)
public abstract class WardenMixin {

    @ModifyReturnValue(method = "canTargetEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$pacifyCanTarget(boolean original) {
        if (original && ModEffects.PACIFIED != null && ((LivingEntity) (Object) this).hasEffect(ModEffects.PACIFIED)) {
            return false;
        }
        return original;
    }
}
