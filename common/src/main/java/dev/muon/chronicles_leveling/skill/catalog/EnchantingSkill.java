package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.EssenceChannellerAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static dev.muon.chronicles_leveling.skill.perk.Perks.unlocks;

/**
 * Enchanting skill tree, built around the enchanting table and grindstone.
 *
 * <p>Every table-touching perk routes through the centralized {@code skill.enchant.EnchantingPerks}, fed by two
 * thin mixin backends: vanilla {@code EnchantmentMenu} (both loaders) and, on NeoForge when Apothic-Enchanting
 * is present, {@code ApothEnchantmentMenu} (it overrides the vanilla flow, so a separate gated mixin is needed).
 */
public final class EnchantingSkill {

    private EnchantingSkill() {}

    /** Summed fraction off the enchanting-table level <i>requirement</i> and levels <i>taken</i> (NOT the seed). */
    public static final SkillCapability<Double> PRODIGY_LEVEL_DISCOUNT = SkillCapability.additive("enchanting_prodigy_level_discount");
    /** Tier (= perk rank) gating Essence Hoarder's per-enchantment resource bonus: 1 mana, 2 +stamina, 3 +health-regen. */
    public static final SkillCapability<Double> ESSENCE_HOARDER_TIER = SkillCapability.additive("enchanting_essence_hoarder_tier");
    /** Most-used enchanting table glows for its owner; items enchanted there gain a permanent +10% base-stat modifier. */
    public static final SkillCapability<Boolean> WIZARDS_STUDY = SkillCapability.flag("enchanting_wizards_study");
    /** Extra enchantment clues the table reveals (= perk rank; the top rank reveals the whole roll). */
    public static final SkillCapability<Double> ARCANE_INSIGHT_REVEAL = SkillCapability.additive("enchanting_arcane_insight_reveal");
    /** Allow non-curse treasure enchantments to roll at the enchanting table. */
    public static final SkillCapability<Boolean> ESOTERIC_ENCHANTER = SkillCapability.flag("enchanting_esoteric_enchanter");
    /** Per-enchant chance (read as skill level %) to roll +1 level, bypassing the enchantment's max. */
    public static final SkillCapability<Boolean> UNSTABLE_POWER = SkillCapability.flag("enchanting_unstable_power");
    /** Every rolled enchantment gains +1 level (bypassing max); stacks with Unstable Power. */
    public static final SkillCapability<Boolean> UNLIMITED_POWER = SkillCapability.flag("enchanting_unlimited_power");
    /** Grindstone Transcribe tier (= perk rank): 1 extracts half onto a book, 2 all, 3 also preserves the source item. */
    public static final SkillCapability<Double> TRANSCRIBE_TIER = SkillCapability.additive("enchanting_transcribe_tier");
    /** Abundance: independent chances (= perk rank) to roll one extra compatible enchant onto the gear (orthogonal to rarity). */
    public static final SkillCapability<Double> ABUNDANCE_TRIALS = SkillCapability.additive("enchanting_abundance_trials");
    /** Experimenter tier (= perk rank): 1 combines same-group damage/protection enchants at the anvil, 2 also lets them co-roll at the table (exclusivity only, never applicability). */
    public static final SkillCapability<Double> EXPERIMENTER_TIER = SkillCapability.additive("enchanting_experimenter_tier");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.ENCHANTING, Component.translatable("chronicles_leveling.skill.enchanting"))
                .description(Component.translatable("chronicles_leveling.skill.enchanting.desc"))

                // Root: Prodigy reaches high-tier enchants at a lower level + spends fewer levels (output unchanged).
                .perk("prodigy").cost(1).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(PRODIGY_LEVEL_DISCOUNT, Configs.SKILLS.enchanting.prodigyLevelDiscountPerRank.get() * rank)))

                // Table-knowledge branch.
                .perk("arcane_insight").requires("prodigy").cost(1).maxRank(3).order(10)
                    .effectsAtRank(rank -> List.of(grant(ARCANE_INSIGHT_REVEAL, Configs.SKILLS.enchanting.arcaneInsightRevealPerRank.get() * rank)))
                .perk("esoteric_enchanter").requires("arcane_insight").cost(3).order(11)
                    .effect(grant(ESOTERIC_ENCHANTER, Boolean.TRUE))
                .perk("unstable_power").requires("arcane_insight").cost(3).order(12)
                    .effect(grant(UNSTABLE_POWER, Boolean.TRUE))
                .perk("unlimited_power").requires("unstable_power").cost(4)
                    .effect(grant(UNLIMITED_POWER, Boolean.TRUE))

                // Resource branch.
                .perk("essence_hoarder").requires("prodigy").cost(1).maxRank(3).order(20)
                    .effectsAtRank(rank -> List.of(grant(ESSENCE_HOARDER_TIER, (double) rank)))

                // Mastery.
                .perk("wizards_study").requires("prodigy").cost(3).order(30)
                    .effect(grant(WIZARDS_STUDY, Boolean.TRUE))

                // Grindstone.
                .perk("transcribe").requires("prodigy").cost(1).maxRank(3).order(40)
                    .effectsAtRank(rank -> List.of(grant(TRANSCRIBE_TIER, (double) rank)))
                .perk("experimenter").requires("transcribe").cost(3).maxRank(2).order(45)
                    .effectsAtRank(rank -> List.of(grant(EXPERIMENTER_TIER, (double) rank)))

                // [Active] Essence Channeller drains XP to repair gear; rank widens the scope (held, worn, all).
                .perk("essence_channeller").requires("prodigy").cost(3).maxRank(3).order(50)
                    .effect(unlocks(EssenceChannellerAbility.ID))
                .ability(new EssenceChannellerAbility())

                // Quantity branch: roll MORE enchants (independent of rarity / Apothic Arcana).
                .perk("abundance").requires("prodigy").cost(3).maxRank(3).order(60)
                    .effectsAtRank(rank -> List.of(grant(ABUNDANCE_TRIALS, Configs.SKILLS.enchanting.abundanceTrialsPerRank.get() * rank)))

                .build();
    }
}
