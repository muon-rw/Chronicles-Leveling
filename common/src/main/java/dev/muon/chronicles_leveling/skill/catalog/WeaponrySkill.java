package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.MastersFocusAbility;
import dev.muon.chronicles_leveling.skill.ability.catalog.SeismicSlamAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Magnitude.perLevel;
import static dev.muon.chronicles_leveling.skill.perk.Perks.attr;
import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static dev.muon.chronicles_leveling.skill.perk.Perks.unlocks;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

/**
 * Weaponry skill tree: three weapon-class branches (Slashing / Piercing / Blunt) plus a shared late lane
 * (Bloodthirst / Momentum / Seismic Slam).
 *
 * <p>Almost every Weaponry node is a <i>conditional</i> combat behavior, so it is a capability the combat-proc
 * layer reads, not an attribute modifier: a weapon-class "+% damage" only applies when the player wields that
 * class of weapon, so an unconditional {@code +%damage} attribute would be wrong. {@code CombatProcRouter}
 * reads {@link dev.muon.chronicles_leveling.skill.SkillEffects} capabilities and applies the bonus only when
 * the weapon class / proc condition matches. The per-seam wiring (which loader hook reads each capability, in
 * what damage phase) lives in {@code CombatProcRouter}'s class doc, the single source of truth.
 *
 * <p>Bloodthirst is the exception: it maps cleanly to {@code combat_attributes:lifesteal} (CA already mitigates
 * the heal generically), so it is a real {@code attr} modifier rather than a capability.
 */
public final class WeaponrySkill {

    private WeaponrySkill() {}

    // Slashing branch.
    /** Summed +% damage with slashing weapons (swords/axes); the pre-armor hook applies it only when a slashing weapon is held. */
    public static final SkillCapability<Double> SLASHING_DAMAGE = SkillCapability.additive("weaponry_slashing_damage");
    /** Chance for a slashing hit to apply a stacking bleed DoT. */
    public static final SkillCapability<Double> REND_CHANCE = SkillCapability.additive("weaponry_rend_chance");
    /** Summed +% attack speed while wielding a slashing weapon (conditional, so a capability not an attribute). */
    public static final SkillCapability<Double> QUICK_BLADE = SkillCapability.additive("weaponry_quick_blade");
    /** Ceiling on the Quick Blade flurry; scales with rank so deeper investment flurries higher. */
    public static final SkillCapability<Double> QUICK_BLADE_MAX_STACKS = SkillCapability.additive("weaponry_quick_blade_max_stacks");
    /** Chance to auto-counter (riposte) a melee hit taken. */
    public static final SkillCapability<Double> RIPOSTE_CHANCE = SkillCapability.additive("weaponry_riposte_chance");

    // Piercing branch.
    /** Summed +% damage with piercing weapons (tridents/spears). */
    public static final SkillCapability<Double> PIERCING_DAMAGE = SkillCapability.additive("weaponry_piercing_damage");
    /** Fraction of target armor ignored; the getDamageAfterAbsorb WrapOperation reads this (clamp at the read site). */
    public static final SkillCapability<Double> ARMOR_PIERCE = SkillCapability.additive("weaponry_armor_pierce");
    /** Pierce hits apply bonus knockback / a brief pin (the post-hit router reads this flag). */
    public static final SkillCapability<Boolean> SKEWER = SkillCapability.flag("weaponry_skewer");
    /** Summed bonus-damage fraction vs. low-health targets; the pre-armor hook applies it below the rank-scaled HP threshold. */
    public static final SkillCapability<Double> EXECUTIONER = SkillCapability.additive("weaponry_executioner");

    // Blunt branch.
    /** Summed +% damage with blunt weapons (maces). */
    public static final SkillCapability<Double> BLUNT_DAMAGE = SkillCapability.additive("weaponry_blunt_damage");
    /** Blunt hits apply the Sunder armor-reduction debuff (custom MobEffect) + extra armor-durability damage. */
    public static final SkillCapability<Boolean> SUNDER = SkillCapability.flag("weaponry_sunder");
    /** Blunt hits drain target CA stamina / stagger (Concussive Blows). */
    public static final SkillCapability<Boolean> CONCUSSIVE_BLOWS = SkillCapability.flag("weaponry_concussive_blows");
    /** Summed bonus-damage fraction of the ATTACKER's max HP added to blunt hits (Heavy Hitter, tanky-bruiser). */
    public static final SkillCapability<Double> HEAVY_HITTER = SkillCapability.additive("weaponry_heavy_hitter");

    // Shared late lane.
    /** Per-stack damage ramp on consecutive hits to one target; the router tracks the streak and resets on miss. */
    public static final SkillCapability<Double> MOMENTUM = SkillCapability.additive("weaponry_momentum");
    /** Ceiling on the Momentum streak; scales with rank so deeper investment ramps higher. */
    public static final SkillCapability<Double> MOMENTUM_MAX_STACKS = SkillCapability.additive("weaponry_momentum_max_stacks");

    /** Adaptive Arsenal capstone: every weapon-class damage bonus and its on-hit proc applies with ANY melee weapon. */
    public static final SkillCapability<Boolean> ADAPTIVE_ARSENAL = SkillCapability.flag("weaponry_adaptive_arsenal");

    private static final Identifier LIFESTEAL = Identifier.fromNamespaceAndPath("combat_attributes", "lifesteal");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.WEAPONRY, Component.translatable("chronicles_leveling.skill.weaponry"))
                .description(Component.translatable("chronicles_leveling.skill.weaponry.desc"))

                // === Slashing branch (root): +% damage with swords/axes, applied only when a slashing weapon is held. ===
                .perk("slashing_focus").cost(1).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(SLASHING_DAMAGE, Configs.SKILLS.weaponry.slashingDamagePerRank.get() * rank)))
                .perk("rend").requires("slashing_focus").cost(2).order(10)
                    .effect(grant(REND_CHANCE, Configs.SKILLS.weaponry.rendChance.get()))
                .perk("quick_blade").requires("slashing_focus").cost(1).maxRank(3).order(20)
                    .effectsAtRank(rank -> List.of(
                            grant(QUICK_BLADE, Configs.SKILLS.weaponry.quickBladeAttackSpeedPerRank.get() * rank),
                            grant(QUICK_BLADE_MAX_STACKS, (double) Configs.SKILLS.weaponry.quickBladeFlurryMaxStacksPerRank.get() * rank)))
                .perk("riposte").requires("rend", "quick_blade", "bloodthirst").requireAny(2).anchorUnderParents().cost(2).order(30)
                    .effect(grant(RIPOSTE_CHANCE, Configs.SKILLS.weaponry.riposteChance.get()))

                // === Piercing branch (root): +% damage with tridents/spears. ===
                .perk("piercing_focus").cost(1).maxRank(3).order(100)
                    .effectsAtRank(rank -> List.of(grant(PIERCING_DAMAGE, Configs.SKILLS.weaponry.piercingDamagePerRank.get() * rank)))
                .perk("armor_pierce").requires("piercing_focus").cost(1).maxRank(3).order(110)
                    .effectsAtRank(rank -> List.of(grant(ARMOR_PIERCE, Configs.SKILLS.weaponry.armorPiercePerRank.get() * rank)))
                .perk("skewer").requires("piercing_focus").cost(1).order(120)
                    .effect(grant(SKEWER, Boolean.TRUE))
                .perk("executioner").requires("armor_pierce", "skewer", "momentum").requireAny(2).anchorUnderParents().cost(2).maxRank(3).order(130)
                    .effectsAtRank(rank -> List.of(grant(EXECUTIONER, Configs.SKILLS.weaponry.executionerBonusPerRank.get() * rank)))

                // === Blunt branch (root): +% damage with maces. ===
                .perk("blunt_focus").cost(1).maxRank(3).order(200)
                    .effectsAtRank(rank -> List.of(grant(BLUNT_DAMAGE, Configs.SKILLS.weaponry.bluntDamagePerRank.get() * rank)))
                .perk("sunder").requires("blunt_focus").cost(2).order(210)
                    .effect(grant(SUNDER, Boolean.TRUE))
                .perk("concussive_blows").requires("blunt_focus").cost(2).order(220)
                    .effect(grant(CONCUSSIVE_BLOWS, Boolean.TRUE))
                .perk("heavy_hitter").requires("sunder", "concussive_blows", "seismic_slam").requireAny(2).anchorUnderParents().cost(1).maxRank(3).order(230)
                    .effectsAtRank(rank -> List.of(grant(HEAVY_HITTER, Configs.SKILLS.weaponry.heavyHitterMaxHpFractionPerRank.get() * rank)))

                // === Each branch's third tier-1 node; with the two finisher-feeders above it, each forms its
                // branch's 2-of-3 gate (slashing -> riposte, piercing -> executioner, blunt -> heavy_hitter). ===
                .perk("bloodthirst").requires("slashing_focus").cost(2).order(300)
                    .effect(attr(LIFESTEAL, ADD_VALUE, perLevel(Configs.SKILLS.weaponry.bloodthirstLifestealPerLevel.get(), Configs.SKILLS.weaponry.bloodthirstLifestealCap.get())))
                .perk("momentum").requires("piercing_focus").cost(2).maxRank(3).order(310)
                    .effectsAtRank(rank -> List.of(
                            grant(MOMENTUM, Configs.SKILLS.weaponry.momentumRampPerRank.get() * rank),
                            grant(MOMENTUM_MAX_STACKS, (double) (Configs.SKILLS.weaponry.momentumMaxStacksPerRank.get() * rank))))
                .perk("seismic_slam").requires("blunt_focus").cost(3).order(320)
                    .effect(unlocks(SeismicSlamAbility.ID))
                .ability(new SeismicSlamAbility())

                // === Capstone lane: the three branches converge: any 2-of-3 unlock the mid finishers,
                // all three gate Master's Focus, and Adaptive Arsenal caps it. ===
                .perk("masters_focus").requires("riposte", "executioner", "heavy_hitter").cost(3).order(330)
                    .effect(unlocks(MastersFocusAbility.ID))
                .ability(new MastersFocusAbility())
                .perk("adaptive_arsenal").requires("masters_focus").cost(3).order(340)
                    .effect(grant(ADAPTIVE_ARSENAL, Boolean.TRUE))

                .build();
    }
}
