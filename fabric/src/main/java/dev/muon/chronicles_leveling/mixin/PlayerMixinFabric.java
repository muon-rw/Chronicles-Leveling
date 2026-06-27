package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.event.TotemHitBridge;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.chronicles_leveling.skill.mobility.AcrobaticsHandler;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.ModStatsFabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player-victim twin of {@link LivingEntityMixinFabric}'s {@code actuallyHurt} seams.
 *
 * <p>{@code Player} <b>overrides</b> {@code actuallyHurt} with its own full copy (no {@code super} call),
 * so a {@code @Mixin(LivingEntity.class)} injector on {@code actuallyHurt} NEVER runs when the victim is a
 * player; it only instruments the mob path. That silently broke three things on Fabric: Pain Tolerance
 * (victim is always a player, so it never capped), and, in PvP, the attacker's pre-armor scalar and the
 * post-hit procs. NeoForge is unaffected (its event seams fire for player victims). So Fabric needs this
 * parallel mixin on {@code Player.actuallyHurt}, whose body has the identical
 * {@code getDamageAfterArmorAbsorb → getDamageAfterMagicAbsorb → setHealth} structure (verified in bytecode),
 * letting the same {@code @At} targets work with {@code Player} as the INVOKE owner.
 *
 * <p>The armor-pen path needs no twin: {@code getDamageAfterArmorAbsorb} itself is NOT overridden by Player,
 * so the common {@code ArmorPenetrationMixin} (which wraps the {@code CombatRules} call inside it) covers
 * players already.
 */
@Mixin(Player.class)
public abstract class PlayerMixinFabric {

    /** Pre-armor outgoing scalar for a player VICTIM (PvP); mirror of {@code LivingEntityMixinFabric}. */
    @WrapOperation(
            method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float chronicles_leveling$preArmorScale(Player victim, DamageSource source, float dmg,
                                                    Operation<Float> original) {
        if (source.getEntity() instanceof ServerPlayer attacker) {
            dmg = CombatProcRouter.modifyOutgoing(attacker, victim, source, dmg);
        }
        return original.call(victim, source, dmg);
    }

    /** Roll fall mitigation then Pain Tolerance's cap on the player victim (mirror of the NeoForge {@code LivingDamageEvent.Pre}). */
    @ModifyExpressionValue(
            method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float chronicles_leveling$reduceIncomingDamage(float mitigated, ServerLevel level, DamageSource source, float dmg) {
        if ((Object) this instanceof ServerPlayer victim) {
            float reduced = AcrobaticsHandler.reduceFallDamage(victim, source, mitigated);
            return CombatProcRouter.capIncoming(victim, source, reduced);
        }
        return mitigated;
    }

    /**
     * Post-mitigation procs for a player victim: PvP {@code onHitDealt} and the victim's reactive
     * {@code onHitTaken} (Riposte / Retribution). Injected AFTER {@code setHealth} so {@code isDeadOrDying}
     * reads the post-hit state. Survivable hits fire procs here; lethal hits stash the amount so the
     * {@code LivingEntityMixinFabric} totem wrap can fire them if a totem saves the victim (matching NeoForge,
     * where {@code LivingDamageEvent.Post} fires after totem processing). {@code finalDmg} is the
     * {@code dmg} arg at this point: the post-armor/post-magic total (health + absorption loss),
     * which is exactly what the NeoForge side feeds {@code onHitTaken}.
     */
    @Inject(
            method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V",
                    shift = At.Shift.AFTER))
    private void chronicles_leveling$playerHit(ServerLevel level, DamageSource source, float originalDmg,
                                               CallbackInfo ci, @Local(argsOnly = true) float finalDmg) {
        if (!((Object) this instanceof ServerPlayer victim)) return;
        if (source.getEntity() instanceof ServerPlayer attacker) {
            CombatProcRouter.onHitDealt(attacker, victim, source, finalDmg);
        }
        if (!victim.isDeadOrDying()) {
            CombatProcRouter.onHitTaken(victim, source, finalDmg);
        } else {
            TotemHitBridge.stashLethal(victim.getId(), finalDmg);
        }
    }

    /**
     * Adds Chronicles' six stat attributes to the player's default supplier on Fabric (NeoForge uses
     * {@code EntityAttributeModificationEvent}; see {@code ModStatsNeoforge}). {@code createAttributes} runs in
     * vanilla's {@code DefaultAttributes <clinit>} during bootstrap, before {@code ModInitializer.onInitialize},
     * so force-touch {@link ModStatsFabric} first to populate the holder map before reading it.
     */
    @ModifyReturnValue(method = "createAttributes", at = @At("RETURN"))
    private static AttributeSupplier.Builder chronicles_leveling$addStats(AttributeSupplier.Builder original) {
        ModStatsFabric.ensureInitialized();
        for (ModStats.Entry stat : ModStats.ALL) {
            original.add(ModStats.get(stat.id()), stat.defaultValue());
        }
        return original;
    }
}
