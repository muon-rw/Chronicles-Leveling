package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import dev.muon.chronicles_leveling.skill.alchemy.PotionPerks;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.chronicles_leveling.skill.combat.QuickBladeHandler;
import dev.muon.chronicles_leveling.skill.enchant.EssenceHoarderHandler;
import dev.muon.chronicles_leveling.skill.mobility.AcrobaticsHandler;
import dev.muon.chronicles_leveling.skill.social.SpeechTamingHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ThrowablePotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityMixin {

    @Shadow protected int useItemRemaining;

    /**
     * Enforces ability i-frame / parry windows on the server-authoritative damage path.
     *
     * <p>Wraps the {@code isInvulnerableTo} call <em>inside {@code hurtServer}</em> (its first gate) rather than
     * injecting the method itself. This is deliberate: {@code isInvulnerableTo} is also called from non-damage
     * probes (e.g. {@code WitherRoseBlock} every tick to gate the wither EFFECT), and a HEAD inject there would
     * burn a one-shot parry charge on a read. Wrapping the {@code hurtServer} call site fires ONLY on an actual
     * damage attempt. Ordering-safe vs Combat-Attributes by construction: CA wraps {@code hurtServer} with
     * {@code @WrapMethod} and calls {@code original.call()}, which runs this wrapped body, so a window negates
     * any hit CA did not already dodge, with no mixin-priority race.
     *
     * <p>Sources tagged {@code BYPASSES_INVULNERABILITY} (/kill, void) or from a creative player are NOT negated,
     * mirroring vanilla's own {@code isInvulnerableToBase} bypass semantics, and the bypass is checked BEFORE
     * {@code consume} so a {@code /kill} can't waste an armed parry.
     */
    @WrapOperation(
            method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isInvulnerableTo(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)Z"),
            remap = false)
    private boolean chronicles_leveling$abilityWindows(LivingEntity self, ServerLevel level, DamageSource source,
                                                       Operation<Boolean> original) {
        if (self instanceof ServerPlayer player
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer()) {
            if (AbilityWindowStore.isActive(player, AbilityWindowStore.WindowKind.IFRAME)) {
                return true;   // i-frame: negate the hit (vanilla reads true as "invulnerable")
            }
            if (AbilityWindowStore.consume(player, AbilityWindowStore.WindowKind.PARRY_ARMED)) {
                // Successful parry: a distinct, higher-pitched cue so it reads differently from the arm.
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.5f);
                return true;
            }
        }
        return original.call(self, level, source);
    }

    /**
     * Weaponry "Armor Pierce": lets a player ignore a fraction of the target's armor.
     *
     * <p>Vanilla armor math is nonlinear ({@code CombatRules.getDamageAfterAbsorb} clamps effective armor
     * by hit size and toughness), so faking penetration by inflating pre-armor damage only approximates it.
     * Instead we wrap the {@code CombatRules.getDamageAfterAbsorb(...)} INVOKE inside
     * {@code LivingEntity#getDamageAfterArmorAbsorb} and recompute with {@code armor * (1 - pierce)}: exact,
     * because it reuses the real curve, and scoped to physical armor only (CA's {@code magic_defense} is a
     * separate path). One common code path on both loaders; sits inside CA's {@code original.call}, so no
     * mixin-priority race. {@code remap = false} like every common mixin here (Mojmap runtime fork).
     */
    @WrapOperation(
            method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"),
            remap = false)
    private float chronicles_leveling$armorPierce(LivingEntity victim, float damage, DamageSource source,
                                                  float armor, float toughness, Operation<Float> original) {
        double pierce = CombatProcRouter.armorPierce(source);
        if (pierce > 0) {
            armor *= (float) (1.0 - Math.min(pierce, 0.95));   // never fully strip armor (clamp the summed fraction)
        }
        return original.call(victim, damage, source, armor, toughness);
    }

    /**
     * Iron Stomach: shortens HARMFUL effects as they are applied to a perk holder. Targets the 2-arg
     * {@code addEffect} (the 1-arg overload is final and delegates here) plus {@code forceAddEffect}, covering
     * every server-side application path; the ServerPlayer gate inside the perk read keeps the client's synced
     * re-application from double-shortening.
     */
    @ModifyVariable(method = {
            "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V"},
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance chronicles_leveling$ironStomachShortenHarmful(MobEffectInstance effect) {
        double reduction = PotionPerks.harmfulDurationReduction((LivingEntity) (Object) this);
        if (reduction <= 0.0
                || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL
                || effect.getEffect().value().isInstantenous()
                || effect.isInfiniteDuration()) {
            return effect;
        }
        int duration = Math.max(1, (int) (effect.getDuration() * (1.0 - reduction)));
        return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(),
                effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    /** Wide Block Arc: collapse the attack angle so {@code resolveBlockedDamage} always treats the hit as in-cone. */
    @ModifyArg(method = "applyItemBlocking",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"),
            index = 2,
            remap = false)
    private double chronicles_leveling$wideBlockArc(double angle) {
        return CombatProcRouter.wideBlockAngle((LivingEntity) (Object) this, angle);
    }

    /** Shield Bash: on a successful block, a chance to fully negate the hit and stun the attacker. */
    @ModifyReturnValue(method = "applyItemBlocking", at = @At("RETURN"), remap = false)
    private float chronicles_leveling$shieldBash(float damageBlocked,
            @Local(argsOnly = true) DamageSource source, @Local(argsOnly = true) float damage) {
        return CombatProcRouter.shieldBash((LivingEntity) (Object) this, source, damage, damageBlocked);
    }

    /** Stalwart: full knockback resistance while blocking or sneaking. */
    @ModifyExpressionValue(method = "knockback",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"),
            remap = false)
    private double chronicles_leveling$stalwartKnockback(double resistance) {
        return CombatProcRouter.isBlockGuarding((LivingEntity) (Object) this) ? 1.0 : resistance;
    }

    /** Stalwart: reject movement-slowing effects while blocking or sneaking. */
    @ModifyReturnValue(method = "canBeAffected", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$stalwartEffectImmunity(boolean original, @Local(argsOnly = true) MobEffectInstance newEffect) {
        return original && !CombatProcRouter.stalwartBlocksEffect((LivingEntity) (Object) this, newEffect);
    }

    /**
     * Quick Quaff: removes a fraction of the drink's duration by decrementing {@code useItemRemaining} an extra amount each
     * tick, spread out evenly (the Combat-Attributes draw-speed trick). {@code updateUsingItem} ticks on both the client
     * (animation) and the server (finish timing), and the drink-speed fraction is read on either side, so the two stay in
     * sync. Unlike a charged bow (which releases manually), a drink auto-completes when {@code useItemRemaining} hits 0, so
     * the value is clamped to at least 1 in this HEAD inject; the vanilla {@code --useItemRemaining} then reaches exactly 0
     * and completes, never skipping past it.
     */
    @Inject(method = "updateUsingItem", at = @At("HEAD"))
    private void chronicles_leveling$quickQuaff(ItemStack useItem, CallbackInfo ci) {
        if (!(useItem.getItem() instanceof PotionItem) || useItem.getItem() instanceof ThrowablePotionItem) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        double speed = PotionPerks.quickQuaff(self);
        if (speed <= 0.0) {
            return;
        }
        int extra = chronicles_leveling$quaffDelta(self.tickCount, speed);
        if (extra != 0) {
            this.useItemRemaining = Math.max(1, this.useItemRemaining + extra);
        }
    }

    /**
     * Negative extra decrement for this tick. {@code speed} is the fraction of drink time to remove, so the extra
     * progress rate that delivers it is {@code speed / (1 - speed)}. That rate is distributed evenly across ticks
     * (Bresenham, keyed on {@code tickCount}) so the per-tick average is exact and lands identically on client and
     * server. Speed is capped at 0.9 to keep the rate finite.
     */
    @Unique
    private static int chronicles_leveling$quaffDelta(int tickCount, double speed) {
        double rate = speed / (1.0 - Math.min(0.9, speed));
        long now = (long) Math.floor(tickCount * rate);
        long previous = (long) Math.floor((tickCount - 1) * rate);
        return -(int) (now - previous);
    }

    @Inject(method = "handleEquipmentChanges", at = @At("HEAD"))
    private void chronicles_leveling$onEquipmentChanged(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            EssenceHoarderHandler.onEquipmentChanged(player);
            QuickBladeHandler.onEquipmentChanged(player);
        }
    }

    /**
     * Off-hand (F-key) swap path: {@code handleHandSwap} strips the pure main/off swap from the change set, so the
     * hook above never sees it. Inject inside the swap-detected branch (the tracking-player swap-animation packet,
     * which only runs once a real swap is confirmed) so the dynamic modifiers update immediately on the swap rather
     * than one poll later. {@code getMainHandItem} already reflects the swapped item at this point.
     */
    @Inject(method = "handleHandSwap",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerChunkCache;sendToTrackingPlayers(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/Packet;)V"))
    private void chronicles_leveling$onHandSwap(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            EssenceHoarderHandler.onEquipmentChanged(player);
            QuickBladeHandler.onEquipmentChanged(player);
        }
    }

    /** Pack Leader (Speech): reduce incoming damage to a holder's tamed mob. Player overrides actuallyHurt, so this hits only mobs. */
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true, remap = false)
    private float chronicles_leveling$packLeaderReduction(float dmg) {
        return SpeechTamingHandler.reducePetDamage((LivingEntity) (Object) this, dmg);
    }

    /** Catlike (Acrobatics): reduces a sneaking holder's visibility to targeting mobs (shrinks their detection range). */
    @ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"), remap = false)
    private double chronicles_leveling$reducedDetection(double original) {
        return AcrobaticsHandler.reduceVisibility((LivingEntity) (Object) this, original);
    }
}
