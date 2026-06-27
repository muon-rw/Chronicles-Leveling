package dev.muon.chronicles_leveling.skill.social;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.effect.ModEffects;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.SpeechSkill;
import dev.muon.chronicles_leveling.skill.combat.TargetAllegiance;
import dev.muon.chronicles_leveling.skill.util.EntityRaycast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

import java.util.List;

/** Loader-agnostic Speech taming/beast hooks: Pack Leader, Kindred Fury, Husbandry, Beast Whisperer, Pacify. */
public final class SpeechTamingHandler {

    private SpeechTamingHandler() {}

    private static final int PARENT_AGE_AFTER_BREEDING = 6000;
    private static final int MIN_BREEDING_COOLDOWN = 600;

    /** The owning holder of a mob, or null when it is not a tamed mob of a skilled player. */
    private static ServerPlayer ownerHolder(Entity mob) {
        return mob instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer player ? player : null;
    }

    /** Pack Leader: reduce incoming damage to a holder's tamed mob by the rank fraction. */
    public static float reducePetDamage(LivingEntity mob, float dmg) {
        ServerPlayer owner = ownerHolder(mob);
        if (owner == null) {
            return dmg;
        }
        double reduction = SkillEffects.get(owner, SpeechSkill.PACK_LEADER_REDUCTION);
        return reduction > 0 ? (float) (dmg * (1.0 - Math.min(1.0, reduction))) : dmg;
    }

    /** Kindred Fury: the damage multiplier for a holder, scaling with how many of their pets stand nearby. */
    public static double kindredFuryMultiplier(ServerPlayer owner) {
        double perPet = SkillEffects.get(owner, SpeechSkill.KINDRED_FURY);
        return perPet > 0 ? 1.0 + perPet * countPetsNear(owner) : 1.0;
    }

    /** Kindred Fury: multiplier for a pet's own attack, read off the pet's owning holder. */
    public static double petKindredFuryMultiplier(Mob mob) {
        ServerPlayer owner = ownerHolder(mob);
        return owner != null ? kindredFuryMultiplier(owner) : 1.0;
    }

    private static int countPetsNear(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return 0;
        }
        double radius = Configs.SKILLS.speech.kindredFuryRadius.get();
        int near = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(radius),
                e -> e instanceof OwnableEntity owned && owned.getOwner() == owner).size();
        return Math.min(near, Configs.SKILLS.speech.kindredFuryMaxPets.get());
    }

    /** Husbandry: shorten the post-breeding cooldown for a pair the holder bred together. */
    public static void shortenBreedingCooldown(Animal first, Animal second, ServerPlayer cause) {
        if (cause == null) {
            return;
        }
        double reduction = SkillEffects.get(cause, SpeechSkill.HUSBANDRY);
        if (reduction <= 0) {
            return;
        }
        int age = Math.max(MIN_BREEDING_COOLDOWN, (int) (PARENT_AGE_AFTER_BREEDING * (1.0 - Math.min(1.0, reduction))));
        first.setAge(age);
        second.setAge(age);
    }

    /** Beast Whisperer: the un-tamed tameable the holder is looking at within range, or null. Side-effect free (for canActivate). */
    public static TamableAnimal findTameable(ServerPlayer player, double range) {
        return EntityRaycast.lookingAt(player, range, e -> e instanceof TamableAnimal t && !t.isTame())
                instanceof TamableAnimal tamable ? tamable : null;
    }

    /** Beast Whisperer: raycast for a tameable the holder is looking at and instantly tame it; returns success. */
    public static boolean instantTame(ServerPlayer player, double range) {
        TamableAnimal tamable = findTameable(player, range);
        if (tamable != null) {
            tamable.tame(player);
            return true;
        }
        return false;
    }

    /** Pacify: the nearby mobs eligible to be pacified by the holder. Side-effect free (for canActivate). */
    public static List<Mob> pacifiableMobs(ServerPlayer player, double radius) {
        if (!(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                m -> TargetAllegiance.isPacifiable(player, m));
    }

    /** Pacify: clear the target of nearby non-allied targeting mobs and stop them re-acquiring for the duration. */
    public static int pacifyArea(ServerPlayer player, double radius, int durationTicks) {
        if (ModEffects.PACIFIED == null) {
            return 0;
        }
        int count = 0;
        for (Mob mob : pacifiableMobs(player, radius)) {
            mob.setTarget(null);
            mob.addEffect(new MobEffectInstance(ModEffects.PACIFIED, durationTicks, 0, false, true, true));
            count++;
        }
        return count;
    }
}
