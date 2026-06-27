package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.event.TotemHitBridge;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.chronicles_leveling.skill.mobility.AcrobaticsHandler;
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
public class LivingEntityMixinFabric {

    /**
     * Fabric has no jump event; NeoForge gets {@code LivingEvent.LivingJumpEvent}
     * for free, so this mixin is the loader-side substitute (Acrobatics jump XP +
     * Momentum Vault speed burst). Server-only check keeps client prediction from double-counting.
     */
    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void chronicles_leveling$onJump(CallbackInfo ci) {
        if (((LivingEntity) (Object) this) instanceof ServerPlayer player) {
            JumpXpHandler.onJump(player);
            AcrobaticsHandler.onJump(player);
        }
    }

    /**
     * The MOB-victim dealt seam (player victims go through {@code PlayerMixinFabric}, since {@code Player}
     * overrides {@code actuallyHurt}). Vanilla reassigns the float {@code dmg} arg (slot 3) in place
     * through {@code getDamageAfterArmorAbsorb} then {@code getDamageAfterMagicAbsorb}, so by {@code setHealth}
     * it holds the post-armor/post-magic total (health loss + absorption loss); exactly what the NeoForge
     * side and {@code PlayerMixinFabric} feed {@code onHitDealt}. {@code @Local(argsOnly = true)} captures that
     * single float arg; the raw health decrement is a SEPARATE non-arg local ({@code max(dmg - absorption, 0)})
     * and is intentionally NOT what we read. Injected {@code shift = AFTER} (matching {@code PlayerMixinFabric}) so
     * both seams observe identical post-{@code setHealth} state; slot 3 stays in scope and unwritten there.
     */
    @Inject(
            method = "actuallyHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V",
                    shift = At.Shift.AFTER)
    )
    private void chronicles_leveling$grantDealtXp(
            ServerLevel level, DamageSource source, float originalDmg, CallbackInfo ci,
            @Local(argsOnly = true) float finalDmg) {
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        LivingEntity victim = (LivingEntity) (Object) this;
        DamageXpRouter.onDamageDealt(attacker, victim, source, finalDmg);
        CombatProcRouter.onHitDealt(attacker, victim, source, finalDmg);
    }

    /**
     * Pre-armor outgoing scalar (weapon-class +%, Executioner, Heavy Hitter, Momentum, Far Shot).
     * Wrapping the {@code getDamageAfterArmorAbsorb} call, rather than {@code @ModifyVariable}-ing the
     * {@code dmg} arg, is what gives the handler the {@link DamageSource} it needs to find the attacker.
     * Runs inside CA's {@code original.call} (CA crit-boosts {@code dmg} first), so this scales the
     * post-crit, pre-armor amount; the NeoForge side uses {@code LivingIncomingDamageEvent} for parity.
     */
    @WrapOperation(
            method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float chronicles_leveling$preArmorScale(LivingEntity victim, DamageSource source, float dmg,
                                                    Operation<Float> original) {
        if (source.getEntity() instanceof ServerPlayer attacker) {
            dmg = CombatProcRouter.modifyOutgoing(attacker, victim, source, dmg);
        }
        return original.call(victim, source, dmg);
    }
    // Pain Tolerance lives in PlayerMixinFabric, not here: the victim is always a player, and Player overrides
    // actuallyHurt, so a LivingEntity-targeted cap would never run on the player path.

    /**
     * Totem-save seam. Vanilla consults {@code checkTotemDeathProtection} in {@code hurtServer} once the
     * victim is dead-or-dying, after the {@code actuallyHurt} seams above ({@code Player.hurtServer}
     * delegates to {@code super}, so this covers player victims). A save means the victim survived the
     * hit: fire the reactive procs with the amount {@code PlayerMixinFabric} stashed, and mark the save so the
     * {@code AFTER_DAMAGE} taken-XP handler skips the hit (NeoForge parity on both counts; its
     * {@code LivingDamageEvent.Post} fires after totem processing). The stash is consumed on the death
     * path too, so it cannot go stale.
     */
    @WrapOperation(
            method = "hurtServer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;checkTotemDeathProtection(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean chronicles_leveling$totemSaveProcs(LivingEntity self, DamageSource source, Operation<Boolean> original) {
        boolean saved = original.call(self, source);
        float pending = TotemHitBridge.consumeLethal(self.getId());
        if (saved && self instanceof ServerPlayer victim) {
            TotemHitBridge.markSaved(victim.getId());
            if (pending > 0f) {
                CombatProcRouter.onHitTaken(victim, source, pending);
            }
        }
        return saved;
    }
}
