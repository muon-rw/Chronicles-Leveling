package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.combat.TargetAllegiance;
import dev.muon.chronicles_leveling.skill.util.AbilityTargets;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Weaponry (Blunt): a ground slam that deals AoE damage and staggers (Slowness) every nearby enemy.
 * Lands as a normal player melee hit, so it carries the slammer's weapon-class procs (Sunder, Concussive)
 * and damage modifiers onto the whole group; the on-hit procs it triggers are all non-damaging, so there
 * is no proc cascade.
 */
public final class SeismicSlamAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/seismic_slam");

    public SeismicSlamAbility() {
        super(ID, Skills.WEAPONRY,
                Configs.SKILLS.weaponry.seismicSlamCooldownTicks.get(),
                AbilityCost.stamina(Configs.SKILLS.weaponry.seismicSlamStaminaCost.get().floatValue()));
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        double radius = Configs.SKILLS.weaponry.seismicSlamRadius.get();
        return AbilityTargets.anyEntityWithin(player, radius, LivingEntity.class, e -> isTarget(player, e, radius));
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.no_hostiles");
    }

    @Override
    public void run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        var w = Configs.SKILLS.weaponry;
        double radius = w.seismicSlamRadius.get();
        float damage = w.seismicSlamDamage.get().floatValue();
        int slowTicks = w.seismicSlamSlowTicks.get();
        int slowAmplifier = w.seismicSlamSlowAmplifier.get();
        DamageSource source = player.damageSources().playerAttack(player);
        AABB area = player.getBoundingBox().inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> isTarget(player, e, radius))) {
            target.hurtServer(level, source, damage);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowTicks, slowAmplifier));
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(),
                8, radius / 2.0, 0.1, radius / 2.0, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    private static boolean isTarget(ServerPlayer player, LivingEntity entity, double radius) {
        return TargetAllegiance.isHostileTarget(player, entity) && entity.distanceToSqr(player) <= radius * radius;
    }
}
