package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.skill.alchemy.ToxicologistSpread;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stamps a lingering cloud's instant harmful applications for the owner's Toxicologist kill spread, mirroring the
 * splash hook: instant harm leaves no active effect on a corpse, so the kill check needs the stamp.
 */
@Mixin(value = AreaEffectCloud.class, remap = false)
public abstract class AreaEffectCloudMixin {

    @WrapOperation(method = "serverTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"))
    private void chronicles_leveling$stampInstantHarm(MobEffect self, ServerLevel level, Entity source, Entity owner,
            LivingEntity mob, int amplification, double scale, Operation<Void> original) {
        if (owner instanceof ServerPlayer && self.getCategory() == MobEffectCategory.HARMFUL) {
            ToxicologistSpread.recordInstantHarm(mob, self, amplification);
        }
        original.call(self, level, source, owner, mob, amplification, scale);
    }
}
