package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.skill.xp.DamageXpRouter;
import dev.muon.chronicles_leveling.skill.xp.JumpXpHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    /**
     * Fabric has no jump event; NeoForge gets {@code LivingEvent.LivingJumpEvent}
     * for free, so this mixin is the loader-side substitute. Server-only check
     * keeps client prediction from double-counting.
     */
    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void chronicles_leveling$grantAcrobaticsXp(CallbackInfo ci) {
        if (((LivingEntity) (Object) this) instanceof ServerPlayer player) {
            JumpXpHandler.onJump(player);
        }
    }

    /**
     * Vanilla {@code actuallyHurt} reassigns its {@code dmg} parameter through
     * armor → magic-absorb → absorption-heart reductions, so by the time
     * {@code setHealth} is invoked the parameter slot holds the actual
     * health-decrement. {@code @Local(argsOnly = true)} captures that current
     * slot value rather than the original method argument.
     */
    @Inject(
            method = "actuallyHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V")
    )
    private void chronicles_leveling$grantDealtXp(
            ServerLevel level, DamageSource source, float originalDmg, CallbackInfo ci,
            @Local(argsOnly = true) float finalDmg) {
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        DamageXpRouter.onDamageDealt(attacker, (LivingEntity) (Object) this, source, finalDmg);
    }
}
