package dev.muon.chronicles_leveling.skill.mobility;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.AcrobaticsSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Loader-agnostic Acrobatics damage hooks. Called from the same victim-incoming seams the combat-proc
 * layer rides (NeoForge {@code LivingDamageEvent.Pre}; Fabric {@code Player.actuallyHurt}).
 */
public final class AcrobaticsHandler {

    private AcrobaticsHandler() {}

    /**
     * Roll: landing while looking down cuts fall damage by the rank's fraction (the capstone rank fully
     * negates). The look-down pitch gate keeps it an active landing skill rather than a flat passive.
     */
    public static float reduceFallDamage(ServerPlayer victim, DamageSource source, float amount) {
        if (amount <= 0f || !source.is(DamageTypeTags.IS_FALL)) {
            return amount;
        }
        double reduction = SkillEffects.get(victim, AcrobaticsSkill.ROLL_REDUCTION);
        if (reduction <= 0 || victim.getXRot() < Configs.SKILLS.acrobatics.rollLookDownPitch.get()) {
            return amount;
        }
        return (float) (amount * (1.0 - Math.min(1.0, reduction)));
    }

    /**
     * Catlike: while a holder is sneaking, shrink how visible they are to a targeting mob (scales the mob's
     * effective detection range), stacking on top of vanilla's sneak factor. Read by the visibility mixin.
     */
    public static double reduceVisibility(LivingEntity entity, double original) {
        if (!(entity instanceof ServerPlayer player) || !player.isDiscrete()) {
            return original;
        }
        double reduction = SkillEffects.get(player, AcrobaticsSkill.REDUCED_DETECTION);
        return reduction > 0 ? original * (1.0 - Math.min(1.0, reduction)) : original;
    }

    /** Momentum Vault: a sprint-jump grants a brief movement-speed burst, refreshed on each takeoff. */
    public static void onJump(ServerPlayer player) {
        if (!player.isSprinting() || !SkillEffects.has(player, AcrobaticsSkill.MOMENTUM_VAULT)) {
            return;
        }
        var a = Configs.SKILLS.acrobatics;
        player.addEffect(new MobEffectInstance(MobEffects.SPEED,
                a.momentumVaultSpeedTicks.get(), a.momentumVaultSpeedAmplifier.get(), false, false, true));
    }
}
