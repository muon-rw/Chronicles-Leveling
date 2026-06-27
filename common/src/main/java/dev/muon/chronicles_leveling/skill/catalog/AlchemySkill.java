package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.AlchemicalBatteryAbility;
import dev.muon.chronicles_leveling.skill.ability.catalog.ExperimentalElixirAbility;
import dev.muon.chronicles_leveling.skill.ability.catalog.VolatileElixirAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Magnitude.perLevel;
import static dev.muon.chronicles_leveling.skill.perk.Perks.attr;
import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static dev.muon.chronicles_leveling.skill.perk.Perks.unlocks;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;

/**
 * Alchemy skill tree. Single root: the Experimental Elixir active (rank = how many effects it rolls). Flat
 * utility children hang off the root; two straight school chains run beside them, Restoration (beneficial
 * effects) and Negation (harmful effects), each ending in a mastery node.
 *
 * <p>School nodes give every finished brew or distilled elixir a chance EQUAL TO THE ALCHEMY LEVEL (as a
 * percent) to gain +1 amplifier on their category's effects; mastery nodes make a +1 unconditional, stacking
 * with the school roll. Both ride the identity-preserving {@code BrewPotency} component on brews and bake
 * directly into elixirs. School of Negation also unlocks the Volatile Elixir active (harmful splash; lingering
 * once Negation Mastery is taken).
 *
 * <p>The brewing hooks read these capabilities off the brewing-stand BE attachment ({@code BrewingStationData},
 * keyed to the {@code owner} who loaded the ingredient via {@code AbstractContainerMenuMixin}); the drink/throw/
 * on-kill hooks read them off the player.
 */
public final class AlchemySkill {

    private AlchemySkill() {}

    /** Perk id of the root; its rank (1..3) is the elixir effect count, read by {@code ElixirBrews}. */
    public static final String EXPERIMENTAL_ELIXIR_PERK = "experimental_elixir";

    /** Restoration school: per-brew chance equal to the Alchemy level for +1 amplifier on BENEFICIAL effects. */
    public static final SkillCapability<Boolean> RESTORATION_SCHOOL = SkillCapability.flag("alchemy_school_restoration");
    /** Restoration mastery: brewed/distilled BENEFICIAL effects always gain +1 amplifier (stacks with the school roll). */
    public static final SkillCapability<Boolean> RESTORATION_MASTERY = SkillCapability.flag("alchemy_restoration_mastery");
    /** Negation school: per-brew chance equal to the Alchemy level for +1 amplifier on HARMFUL effects. */
    public static final SkillCapability<Boolean> NEGATION_SCHOOL = SkillCapability.flag("alchemy_school_negation");
    /** Negation mastery: brewed/distilled HARMFUL effects always gain +1 amplifier; Volatile Elixirs become lingering. */
    public static final SkillCapability<Boolean> NEGATION_MASTERY = SkillCapability.flag("alchemy_negation_mastery");
    /** Summed chance (clamp at the read site) that a completed brew yields an extra output potion. */
    public static final SkillCapability<Double> EXTRA_BREW_CHANCE = SkillCapability.additive("alchemy_extra_brew_chance");
    /** Fraction of brewing time saved (faster brew). */
    public static final SkillCapability<Double> BREW_SPEED = SkillCapability.additive("alchemy_brew_speed");
    /** Fraction of brewing fuel (blaze powder) consumption avoided. */
    public static final SkillCapability<Double> FUEL_SAVE = SkillCapability.additive("alchemy_fuel_save");
    /** Fractional bonus to brewed-effect durations; also applied to distilled elixirs. */
    public static final SkillCapability<Double> LINGERING_TOUCH = SkillCapability.additive("alchemy_lingering_touch");
    /** Fraction of duration removed from HARMFUL effects applied to the holder. */
    public static final SkillCapability<Double> IRON_STOMACH = SkillCapability.additive("alchemy_iron_stomach");
    /** Fractional bonus to splash/lingering cloud radius the player throws. */
    public static final SkillCapability<Double> EMPOWERED_SPLASH = SkillCapability.additive("alchemy_empowered_splash");
    /** Toxicologist: killing an enemy afflicted by a HARMFUL effect spreads its effects to nearby enemies. */
    public static final SkillCapability<Boolean> TOXICOLOGIST = SkillCapability.flag("alchemy_toxicologist");
    /** Fraction of potion drink time saved (faster quaff). */
    public static final SkillCapability<Double> QUICK_QUAFF = SkillCapability.additive("alchemy_quick_quaff");
    /** Deft Hands: the holder's potion drink/throw cooldowns are skipped. */
    public static final SkillCapability<Boolean> POTION_COOLDOWN_BYPASS = SkillCapability.flag("alchemy_potion_cooldown_bypass");

    // Light, thematic attribute passive on the school nodes: an alchemist's distilled potions teach faster.
    private static final Identifier EXPERIENCE_GAIN = Identifier.fromNamespaceAndPath("combat_attributes", "experience_gain");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.ALCHEMY, Component.translatable("chronicles_leveling.skill.alchemy"))
                .description(Component.translatable("chronicles_leveling.skill.alchemy.desc"))

                // === Root: the Experimental Elixir active; each rank adds one rolled effect (1..3). ===
                .perk(EXPERIMENTAL_ELIXIR_PERK).cost(2).maxRank(3)
                    .effect(unlocks(ExperimentalElixirAbility.ID))
                .ability(new ExperimentalElixirAbility())

                // === Flat utility children (the school nodes interleave at slots 3 and 5) ===
                .perk("catalysis").requires(EXPERIMENTAL_ELIXIR_PERK).cost(2).maxRank(2).order(10)
                    .effectsAtRank(rank -> List.of(
                            grant(BREW_SPEED, Configs.SKILLS.alchemy.brewSpeedPerRank.get() * rank),
                            grant(FUEL_SAVE, Configs.SKILLS.alchemy.fuelSavePerRank.get() * rank)))
                .perk("deft_hands").requires(EXPERIMENTAL_ELIXIR_PERK).cost(3).order(20)
                    .effect(grant(POTION_COOLDOWN_BYPASS, Boolean.TRUE))
                .perk("lingering_touch").requires(EXPERIMENTAL_ELIXIR_PERK).cost(2).maxRank(2).order(40)
                    .effectsAtRank(rank -> List.of(grant(LINGERING_TOUCH, Configs.SKILLS.alchemy.lingeringTouchPerRank.get() * rank)))
                .perk("iron_stomach").requires(EXPERIMENTAL_ELIXIR_PERK).cost(3).order(60)
                    .effect(grant(IRON_STOMACH, Configs.SKILLS.alchemy.ironStomachReduction.get()))
                .perk("master_brewer").requires(EXPERIMENTAL_ELIXIR_PERK).cost(2).maxRank(3).order(70)
                    .effectsAtRank(rank -> List.of(grant(EXTRA_BREW_CHANCE, Configs.SKILLS.alchemy.extraBrewChancePerRank.get() * rank)))

                // === Restoration chain: school -> quick quaff -> battery -> mastery ===
                .perk("school_restoration").requires(EXPERIMENTAL_ELIXIR_PERK).cost(2).order(30)
                    .effect(grant(RESTORATION_SCHOOL, Boolean.TRUE))
                    .effect(attr(EXPERIENCE_GAIN, ADD_MULTIPLIED_BASE, perLevel(Configs.SKILLS.alchemy.schoolExperienceGainPerLevel.get(), Configs.SKILLS.alchemy.schoolExperienceGainCap.get())))
                .perk("quick_quaff").requires("school_restoration").cost(2).maxRank(3).order(110)
                    .effectsAtRank(rank -> List.of(grant(QUICK_QUAFF, Configs.SKILLS.alchemy.quickQuaffPerRank.get() * rank)))
                .perk("alchemical_battery").requires("quick_quaff").cost(5).order(120)
                    .effect(unlocks(AlchemicalBatteryAbility.ID))
                .ability(new AlchemicalBatteryAbility())
                .perk("restoration_mastery").requires("alchemical_battery").cost(3).order(130)
                    .effect(grant(RESTORATION_MASTERY, Boolean.TRUE))

                // === Negation chain: school (+ Volatile Elixir active) -> splash -> toxicologist -> mastery ===
                .perk("school_negation").requires(EXPERIMENTAL_ELIXIR_PERK).cost(2).order(50)
                    .effect(grant(NEGATION_SCHOOL, Boolean.TRUE))
                    .effect(unlocks(VolatileElixirAbility.ID))
                    .effect(attr(EXPERIENCE_GAIN, ADD_MULTIPLIED_BASE, perLevel(Configs.SKILLS.alchemy.schoolExperienceGainPerLevel.get(), Configs.SKILLS.alchemy.schoolExperienceGainCap.get())))
                .ability(new VolatileElixirAbility())
                .perk("empowered_splash").requires("school_negation").cost(2).order(210)
                    .effect(grant(EMPOWERED_SPLASH, Configs.SKILLS.alchemy.empoweredSplashBonus.get()))
                .perk("toxicologist").requires("empowered_splash").cost(3).order(220)
                    .effect(grant(TOXICOLOGIST, Boolean.TRUE))
                .perk("negation_mastery").requires("toxicologist").cost(3).order(230)
                    .effect(grant(NEGATION_MASTERY, Boolean.TRUE))

                .build();
    }
}
