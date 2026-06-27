package dev.muon.chronicles_leveling.skill.combat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.effect.BleedMobEffect;
import dev.muon.chronicles_leveling.effect.ModEffects;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.ability.CombatResources;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import dev.muon.chronicles_leveling.skill.alchemy.ToxicologistSpread;
import dev.muon.chronicles_leveling.skill.catalog.ArcherySkill;
import dev.muon.chronicles_leveling.skill.catalog.DefenseSkill;
import dev.muon.chronicles_leveling.skill.catalog.WeaponrySkill;
import dev.muon.chronicles_leveling.skill.social.SpeechTamingHandler;
import dev.muon.combat_attributes.attribute.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The loader-agnostic combat-proc layer, the behavioral sibling of {@link dev.muon.chronicles_leveling.skill.xp.DamageXpRouter}.
 * It reads {@link SkillEffects} capabilities a player earned in the Weaponry / Archery / Defense
 * trees and turns them into damage adjustments, knockback, status effects, and counter-hits.
 *
 * <p>Ordering vs. Combat-Attributes is correct by construction. CA owns one {@code @WrapMethod}
 * on {@code hurtServer}: it crit-boosts {@code damage} <em>before</em> {@code original.call}, then
 * runs the vanilla body. So every seam below runs <em>after</em> CA's crit:
 * <ul>
 *   <li><b>{@link #modifyOutgoing}, pre-armor scalar.</b> Weapon-class +%, Executioner, Heavy Hitter,
 *       Momentum, Far Shot. Called from NeoForge {@code LivingIncomingDamageEvent} ({@code setAmount})
 *       and a Fabric {@code @WrapOperation} on the {@code getDamageAfterArmorAbsorb} call inside
 *       {@code actuallyHurt}; both pre-armor, both inside CA's {@code original.call}.</li>
 *   <li><b>{@link #armorPierce}, armor recompute.</b> A common {@code @WrapOperation} on the
 *       {@code CombatRules.getDamageAfterAbsorb} INVOKE recomputes mitigation with reduced armor,
 *       using the real (nonlinear) vanilla curve.</li>
 *   <li><b>{@link #capIncoming}, Pain Tolerance.</b> Caps a single hit's realised damage. NeoForge
 *       {@code LivingDamageEvent.Pre} ({@code setNewDamage}); Fabric {@code @ModifyExpressionValue}
 *       on {@code getDamageAfterMagicAbsorb} (post-mitigation, pre-absorption-split).</li>
 *   <li><b>{@link #onHitDealt} / {@link #onHitTaken}, post-mitigation procs.</b> Skewer, Disorient,
 *       Pinning, Momentum bookkeeping / Riposte, Retribution. Ride the SAME post hooks the XP router
 *       uses (NeoForge {@code LivingDamageEvent.Post}; Fabric {@code actuallyHurt} {@code @Local} +
 *       {@code AFTER_DAMAGE}), strictly after the hit landed.</li>
 * </ul>
 *
 * <p>Server-thread only. Two pieces of transient state: a per-attacker Momentum streak (dropped on
 * logout via {@link #clear}) and a re-entrancy flag so a Riposte/Retribution counter-hit can't spawn
 * further procs (or get scaled like a real attack).
 */
public final class CombatProcRouter {

    private CombatProcRouter() {}

    // All proc tuning lives in config (per-skill sections of ConfigSkills), read live per hit; see the
    // Weaponry / Archery / Defense sections. Server-authoritative + synced, like the XP curves.

    /** Re-entrancy guard: proc-induced damage must not trigger further procs. Server thread only. */
    private static boolean inProc = false;

    /** Master's Focus bonus damage type: bypasses armor, resistances, enchantments, shields, and i-frames. */
    private static final ResourceKey<DamageType> TRUE_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ChroniclesLeveling.id("true_damage"));

    /** A run of consecutive hits by one attacker on one target (entity id), for Momentum. */
    private record Streak(int victimId, int stacks, long tick) {}

    private static final Map<UUID, Streak> STREAKS = new HashMap<>();

    /** Game-time tick at which a player's Last Stand comes off cooldown (Defense). Server thread only. */
    private static final Map<UUID, Long> LAST_STAND_READY = new HashMap<>();

    /**
     * Victim UUID -> the player UUID whose Rend applied the active bleed, so {@link BleedMobEffect}'s
     * over-time ticks can credit the kill. Transient (like {@link #STREAKS}): overwritten on re-bleed,
     * cleared when the bleed kills its victim; a surviving victim's stale entry is harmless and is
     * reclaimed on its next bleed or death.
     */
    private static final Map<UUID, UUID> BLEED_ATTACKERS = new HashMap<>();

    /** Whether the current hit is proc-induced (Riposte / Retribution / Master's Focus true damage), so other seams skip it. */
    public static boolean isProcDamage() {
        return inProc;
    }

    /** Drops the player's transient combat state on logout (the maps only track online players). */
    public static void clear(UUID playerId) {
        STREAKS.remove(playerId);
        LAST_STAND_READY.remove(playerId);
    }

    // === Pre-armor outgoing scalar ===

    /**
     * Adjusts the (post-CA-crit, pre-armor) damage a player is about to deal. Reads the attacker's
     * capabilities; reads but does not mutate the Momentum streak (the streak advances in
     * {@link #onHitDealt}, which fires later in the same hit, so hit #1 gets no ramp).
     */
    public static float modifyOutgoing(ServerPlayer attacker, LivingEntity victim, DamageSource source, float amount) {
        if (inProc || amount <= 0f) {
            return amount;
        }
        WeaponClass wc = WeaponClass.classify(attacker, source);
        boolean adaptive = wc.isMelee() && SkillEffects.has(attacker, WeaponrySkill.ADAPTIVE_ARSENAL);

        double mult = 1.0;
        float flat = 0f;

        // Weapon-class +% damage: only the branch matching the weapon in hand, unless Adaptive Arsenal grants all three.
        if (adaptive) {
            mult += SkillEffects.get(attacker, WeaponrySkill.SLASHING_DAMAGE)
                    + SkillEffects.get(attacker, WeaponrySkill.PIERCING_DAMAGE)
                    + SkillEffects.get(attacker, WeaponrySkill.BLUNT_DAMAGE);
        } else {
            mult += switch (wc) {
                case SLASHING -> SkillEffects.get(attacker, WeaponrySkill.SLASHING_DAMAGE);
                case PIERCING -> SkillEffects.get(attacker, WeaponrySkill.PIERCING_DAMAGE);
                case BLUNT -> SkillEffects.get(attacker, WeaponrySkill.BLUNT_DAMAGE);
                default -> 0.0;
            };
        }

        // Kindred Fury: bonus damage scaling with how many of the attacker's pets stand nearby (any attack).
        mult += SpeechTamingHandler.kindredFuryMultiplier(attacker) - 1.0;

        if (wc.isMelee()) {
            // Executioner: bonus vs. low-health targets (earned in the Piercing branch; applies on any melee).
            // Both the window AND the bonus grow with rank (design: "threshold rises with rank"), derived from
            // the single summed capability value rather than a second key.
            double executioner = SkillEffects.get(attacker, WeaponrySkill.EXECUTIONER);
            if (executioner > 0 && victim.getMaxHealth() > 0f) {
                var w = Configs.SKILLS.weaponry;
                double window = Math.min(w.executionerWindowMax.get(),
                        w.executionerWindowBase.get() + w.executionerWindowPerBonus.get() * executioner);
                if (victim.getHealth() / victim.getMaxHealth() <= window) {
                    mult += executioner;
                }
            }
            // Momentum: ramps with the current streak on this target.
            double momentum = SkillEffects.get(attacker, WeaponrySkill.MOMENTUM);
            if (momentum > 0) {
                mult += momentum * currentStacks(attacker, victim);
            }
            // Heavy Hitter: blunt only (any melee with Adaptive Arsenal); a flat fraction of the ATTACKER's max HP.
            if (wc == WeaponClass.BLUNT || adaptive) {
                double heavy = SkillEffects.get(attacker, WeaponrySkill.HEAVY_HITTER);
                if (heavy > 0) {
                    flat += (float) (attacker.getMaxHealth() * heavy);
                }
            }
        } else {
            // Far Shot: ranged damage scales with how far the arrow actually TRAVELLED. Read the launch
            // position stamped on the arrow (AbstractArrowMixin); fall back to the shooter's current
            // position only if the projectile isn't ours (e.g. a non-arrow ranged source).
            double farShot = SkillEffects.get(attacker, ArcherySkill.FAR_SHOT_BONUS);
            if (farShot > 0) {
                double distSqr;
                if (source.getDirectEntity() instanceof ChroniclesArrow arrow && arrow.chronicles_leveling$launchPos() != null) {
                    distSqr = arrow.chronicles_leveling$launchPos().distanceToSqr(victim.position());
                } else {
                    distSqr = attacker.distanceToSqr(victim);
                }
                mult += farShot * Mth.clamp(Math.sqrt(distSqr) / Configs.SKILLS.archery.farShotMaxRange.get(), 0.0, 1.0);
            }
        }

        float result = Math.max(0f, (float) (amount * mult) + flat);
        // Master's Focus: during the window a natural crit (an attack that would already have critted) adds
        // armor/resistance-bypassing true damage. The forced crit itself lands in CA's rollCrit (the mixin).
        if (wc.isMelee() && AbilityWindowStore.isActive(attacker, AbilityWindowStore.WindowKind.MASTERS_FOCUS)) {
            double critChance = ModAttributes.valueOrDefault(attacker, ModAttributes.meleeCritChance());
            if (critChance > 0 && attacker.getRandom().nextDouble() < critChance) {
                dealTrueDamage(attacker, victim,
                        (float) (result * Configs.SKILLS.weaponry.mastersFocusTrueDamageFraction.get()));
            }
        }
        return result;
    }

    // === Armor penetration (read by the common LivingEntityMixin) ===

    /** Fraction of the victim's armor the attacker ignores (0 if not a player attack). */
    public static double armorPierce(DamageSource source) {
        if (inProc) {
            return 0.0;
        }
        return source.getEntity() instanceof ServerPlayer attacker
                ? SkillEffects.get(attacker, WeaponrySkill.ARMOR_PIERCE)
                : 0.0;
    }

    // === Pain Tolerance (incoming cap) ===

    /**
     * Caps a single hit's realised damage to {@code (1 - fraction)} of the victim's max health (the
     * fraction summed across Pain Tolerance ranks, clamped to {@code defense.painToleranceFloor} so a
     * hit can always remove a minimum slice). Out-of-world / {@code /kill} sources bypass the cap.
     */
    public static float capIncoming(ServerPlayer victim, DamageSource source, float amount) {
        if (inProc || amount <= 0f || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        double fraction = SkillEffects.get(victim, DefenseSkill.MAX_HIT_FRACTION);
        if (fraction <= 0) {
            return amount;
        }
        float cap = (float) (victim.getMaxHealth()
                * (1.0 - Math.min(fraction, Configs.SKILLS.defense.painToleranceFloor.get())));
        return Math.min(amount, cap);
    }

    // === Post-mitigation procs ===

    /** Procs that fire after a player's hit lands (advance Momentum, Skewer KB, projectile CC). */
    public static void onHitDealt(ServerPlayer attacker, LivingEntity victim, DamageSource source, float amount) {
        if (inProc || amount <= 0f) {
            return;
        }
        WeaponClass wc = WeaponClass.classify(attacker, source);

        if (wc.isMelee()) {
            // Adaptive Arsenal (capstone) lets every weapon-class proc fire with any melee weapon.
            boolean adaptive = SkillEffects.has(attacker, WeaponrySkill.ADAPTIVE_ARSENAL);
            if (SkillEffects.get(attacker, WeaponrySkill.MOMENTUM) > 0) {
                bumpStreak(attacker, victim);
            }
            // Skewer: piercing melee adds knockback away from the attacker.
            if ((wc == WeaponClass.PIERCING || adaptive) && SkillEffects.has(attacker, WeaponrySkill.SKEWER)) {
                victim.knockback(Configs.SKILLS.weaponry.skewerKnockback.get(),
                        attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
            }
            // Quick Blade flurry + Rend: slashing hits build attack speed and chance-apply a stacking bleed.
            if (wc == WeaponClass.SLASHING || adaptive) {
                QuickBladeHandler.onSlashingHit(attacker);
                double rend = SkillEffects.get(attacker, WeaponrySkill.REND_CHANCE);
                if (rend > 0 && victim.getRandom().nextDouble() < rend) {
                    applyBleed(victim, attacker);
                }
            }
            // Sunder: blunt hits stack a deepening armor-shred debuff (the effect's modifier scales with amplifier).
            if ((wc == WeaponClass.BLUNT || adaptive) && SkillEffects.has(attacker, WeaponrySkill.SUNDER) && ModEffects.SUNDER != null) {
                var w = Configs.SKILLS.weaponry;
                MobEffectInstance currentSunder = victim.getEffect(ModEffects.SUNDER);
                int amplifier = currentSunder == null ? 0
                        : Math.min(w.sunderMaxStacks.get() - 1, currentSunder.getAmplifier() + 1);
                victim.addEffect(new MobEffectInstance(ModEffects.SUNDER, w.sunderDurationTicks.get(), amplifier));
            }
            // Concussive Blows: blunt hits stagger the target (slowness) and drain a player target's CA stamina.
            if ((wc == WeaponClass.BLUNT || adaptive) && SkillEffects.has(attacker, WeaponrySkill.CONCUSSIVE_BLOWS)) {
                var w = Configs.SKILLS.weaponry;
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        w.concussiveSlownessTicks.get(), w.concussiveSlownessAmplifier.get()));
                if (victim instanceof ServerPlayer victimPlayer) {
                    CombatResources.drainStamina(victimPlayer, w.concussiveStaminaDrain.get().floatValue());
                }
            }
        } else {
            // Disorient / Pinning: ranged crowd control, chance-rolled per hit.
            var archery = Configs.SKILLS.archery;
            double disorient = SkillEffects.get(attacker, ArcherySkill.DISORIENT_CHANCE);
            if (disorient > 0 && victim.getRandom().nextDouble() < disorient) {
                int ticks = archery.disorientDurationTicks.get();
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
                victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, ticks, 0));
            }
            double pinning = SkillEffects.get(attacker, ArcherySkill.PINNING_CHANCE);
            if (pinning > 0 && victim.getRandom().nextDouble() < pinning) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        archery.pinningDurationTicks.get(), archery.pinningAmplifier.get()));
            }
        }
    }

    /** Procs that fire after a player survives a hit, totem saves included on NeoForge (Last Stand on any source; Retribution / Riposte on melee). */
    public static void onHitTaken(ServerPlayer victim, DamageSource source, float amount) {
        if (inProc || amount <= 0f) {
            return;
        }
        tryLastStand(victim);   // any source that leaves the player below the threshold, melee or not
        if (!isMeleeAttack(source) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        ServerLevel level = (ServerLevel) victim.level();

        // Retribution: reflect a fraction of the damage taken (thorns; loop-safe, see isMeleeAttack).
        double reflect = SkillEffects.get(victim, DefenseSkill.RETRIBUTION_REFLECT);
        if (reflect > 0) {
            dealProc(level, attacker, victim.damageSources().thorns(victim), (float) (amount * reflect));
        }

        // Riposte: chance to counter for the player's own attack damage.
        double riposte = SkillEffects.get(victim, WeaponrySkill.RIPOSTE_CHANCE);
        if (riposte > 0 && victim.getRandom().nextDouble() < riposte) {
            float counter = (float) victim.getAttributeValue(Attributes.ATTACK_DAMAGE);
            dealProc(level, attacker, victim.damageSources().thorns(victim), counter);
        }
    }

    /**
     * Player-credited kill procs, driven from a real death event (not {@code isDeadOrDying} at the hit), so a
     * totem-saved victim doesn't count.
     */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        ToxicologistSpread.onKill(killer, victim);
    }

    // === Shield blocking (Defense: Stalwart, Shield Master), read by the common LivingEntityMixin ===

    /**
     * Wide Block Arc: force the block to land when the attack is within the perk's arc half-width. {@code angle} is
     * the radians between the look direction and the attack source (0 ahead, PI behind), so a 360 arc (half-width PI)
     * always lands and a 180 arc (half-width PI/2) covers the front hemisphere. Returns {@code angle} unchanged when
     * the attack is outside the arc (vanilla decides) or the player lacks the perk.
     */
    public static double wideBlockAngle(LivingEntity blocker, double angle) {
        if (!(blocker instanceof ServerPlayer player)) {
            return angle;
        }
        double arcWidthDegrees = SkillEffects.get(player, DefenseSkill.WIDE_BLOCK_ARC);
        if (arcWidthDegrees <= 0) {
            return angle;
        }
        return angle <= Math.toRadians(arcWidthDegrees / 2.0) ? 0.0 : angle;
    }

    /**
     * Shield Bash: on a successful block against a living melee attacker, a per-rank chance to fully negate the hit
     * (returns the full {@code damage}) and stun that attacker. Projectiles and non-living sources can't be bashed,
     * so they block normally ({@code damageBlocked}).
     */
    public static float shieldBash(LivingEntity blocker, DamageSource source, float damage, float damageBlocked) {
        if (damageBlocked <= 0f || !(blocker instanceof ServerPlayer player)) {
            return damageBlocked;
        }
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == player) {
            return damageBlocked;   // no living attacker to stun: not a bash
        }
        double chance = SkillEffects.get(player, DefenseSkill.SHIELD_BASH_CHANCE);
        if (chance <= 0 || player.getRandom().nextDouble() >= Math.min(chance, 1.0)) {
            return damageBlocked;
        }
        var d = Configs.SKILLS.defense;
        attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                d.shieldBashStunTicks.get(), d.shieldBashStunAmplifier.get()));
        attacker.knockback(d.shieldBashKnockback.get(),
                player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        return damage;
    }

    /** Stalwart: active while a holder of the perk is blocking or sneaking (drives knockback + slow immunity). */
    public static boolean isBlockGuarding(LivingEntity entity) {
        return entity instanceof ServerPlayer player && SkillEffects.has(player, DefenseSkill.STALWART)
                && (player.isBlocking() || player.isShiftKeyDown());
    }

    /** Stalwart: whether to reject a movement-slowing effect (Slowness) for a block-guarding player. */
    public static boolean stalwartBlocksEffect(LivingEntity entity, MobEffectInstance effect) {
        return effect.is(MobEffects.SLOWNESS) && isBlockGuarding(entity);
    }

    // === Internals ===

    /** Last Stand: once off cooldown, a hit that leaves the player at or below the threshold grants absorption + resistance. */
    private static void tryLastStand(ServerPlayer victim) {
        if (!SkillEffects.has(victim, DefenseSkill.LAST_STAND) || victim.getMaxHealth() <= 0f) {
            return;
        }
        var d = Configs.SKILLS.defense;
        if (victim.getHealth() / victim.getMaxHealth() > d.lastStandThreshold.get()) {
            return;
        }
        long now = victim.level().getGameTime();
        Long ready = LAST_STAND_READY.get(victim.getUUID());
        if (ready != null && now < ready) {
            return;
        }
        LAST_STAND_READY.put(victim.getUUID(), now + d.lastStandCooldownTicks.get());
        int ticks = d.lastStandDurationTicks.get();
        victim.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, d.lastStandAbsorptionAmplifier.get()));
        victim.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, d.lastStandResistanceAmplifier.get()));
    }

    private static int currentStacks(ServerPlayer attacker, LivingEntity victim) {
        Streak streak = STREAKS.get(attacker.getUUID());
        if (streak == null || streak.victimId() != victim.getId()
                || victim.level().getGameTime() - streak.tick() > Configs.SKILLS.weaponry.momentumResetTicks.get()) {
            return 0;
        }
        return streak.stacks();
    }

    /** Applies Rend's bleed, deepening it by one stack (amplifier) if already present, up to the cap. */
    private static void applyBleed(LivingEntity victim, ServerPlayer attacker) {
        if (ModEffects.BLEED == null) {
            return;
        }
        var w = Configs.SKILLS.weaponry;
        MobEffectInstance current = victim.getEffect(ModEffects.BLEED);
        int amplifier = current == null ? 0 : Math.min(w.rendBleedMaxStacks.get() - 1, current.getAmplifier() + 1);
        victim.addEffect(new MobEffectInstance(ModEffects.BLEED, w.rendBleedDurationTicks.get(), amplifier));
        BLEED_ATTACKERS.put(victim.getUUID(), attacker.getUUID());
    }

    /**
     * The damage source a bleed tick should use: a generic-typed source carrying the player who applied
     * the bleed as its causing entity, so a lethal tick credits that player (loot, advancements, kill
     * tracking). Falls back to an unattributed generic source if the attacker has since logged off.
     */
    public static DamageSource bleedSource(ServerLevel level, LivingEntity victim) {
        UUID attackerId = BLEED_ATTACKERS.get(victim.getUUID());
        ServerPlayer attacker = attackerId == null ? null : level.getServer().getPlayerList().getPlayer(attackerId);
        if (attacker == null) {
            return victim.damageSources().generic();
        }
        Holder<DamageType> generic = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC);
        return new DamageSource(generic, null, attacker);
    }

    public static void clearBleedAttacker(LivingEntity victim) {
        BLEED_ATTACKERS.remove(victim.getUUID());
    }

    private static void bumpStreak(ServerPlayer attacker, LivingEntity victim) {
        var w = Configs.SKILLS.weaponry;
        long now = victim.level().getGameTime();
        Streak streak = STREAKS.get(attacker.getUUID());
        boolean continuing = streak != null && streak.victimId() == victim.getId()
                && now - streak.tick() <= w.momentumResetTicks.get();
        int cap = (int) Math.floor(SkillEffects.get(attacker, WeaponrySkill.MOMENTUM_MAX_STACKS));
        int stacks = continuing ? Math.min(cap, streak.stacks() + 1) : 1;
        STREAKS.put(attacker.getUUID(), new Streak(victim.getId(), stacks, now));
    }

    /** A direct melee strike; excludes projectiles and our own thorns counters (the loop break). */
    private static boolean isMeleeAttack(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypes.THORNS)) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        return direct instanceof LivingEntity && direct == source.getEntity();
    }

    /** Applies counter/reflect damage with the re-entrancy guard raised so it can't cascade. */
    private static void dealProc(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0f) {
            return;
        }
        inProc = true;
        try {
            target.hurtServer(level, source, amount);
        } finally {
            inProc = false;
        }
    }

    /** Deals Master's Focus bonus damage through the bypass-everything {@link #TRUE_DAMAGE} type, attacker-credited. */
    private static void dealTrueDamage(ServerPlayer attacker, LivingEntity victim, float amount) {
        if (amount <= 0f || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TRUE_DAMAGE);
        dealProc(level, victim, new DamageSource(type, attacker), amount);
    }
}
