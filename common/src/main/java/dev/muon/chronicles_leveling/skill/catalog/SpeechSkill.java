package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.BeastWhispererAbility;
import dev.muon.chronicles_leveling.skill.ability.catalog.PacifyAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static dev.muon.chronicles_leveling.skill.perk.Perks.unlocks;

/**
 * Speech skill tree: a trade core that branches into a Bard/trade axis and a Taming axis. The nodes are almost
 * entirely behavioral: villager-trade hooks (read by the trade mixins + {@code SpeechTradeHandler}) and taming
 * hooks (read by {@code SpeechTamingHandler}). Two nodes are actives backed by a registered {@code SkillAbility}:
 * Beast Whisperer (instant-tame raycast) and the Pacify capstone (quiets nearby hostiles).
 */
public final class SpeechSkill {

    private SpeechSkill() {}

    /**
     * Summed per-player trade discount (Haggler). Sell-side applies it as a negative special-price diff
     * on emerald-cost offers; buy-side pays out the same fraction as bonus emeralds.
     */
    public static final SkillCapability<Double> TRADE_DISCOUNT = SkillCapability.additive("speech_trade_discount");
    /** Restocks faster / trade-locks slower at the villager (Reputation, a shared villager benefit). */
    public static final SkillCapability<Boolean> BETTER_RESTOCK = SkillCapability.flag("speech_better_restock");
    /** Multiplier bonus to granted villager XP so villagers level + unlock tiers faster (Master Negotiator). */
    public static final SkillCapability<Double> VILLAGER_XP_BONUS = SkillCapability.additive("speech_villager_xp_bonus");
    /** Extra random enchantments rolled onto an enchantable trade result the holder takes (Enchanted Trader); read by MerchantContainerMixin. */
    public static final SkillCapability<Double> ENCHANTED_TRADER = SkillCapability.additive("speech_enchanted_trader");
    /** Levels added to each non-max-1 enchantment on a bought trade result (Power Broker), +1/+2 by rank. */
    public static final SkillCapability<Double> ENCHANT_LEVEL_BOOST = SkillCapability.additive("speech_enchant_level_boost");
    /** Summed chance a completed trade does NOT consume the offer's stock (Silver Tongue). */
    public static final SkillCapability<Double> NO_STOCK_CONSUME = SkillCapability.additive("speech_no_stock_consume");

    /** Wandering traders spawn more often near a holder (Wandering Eye). */
    public static final SkillCapability<Boolean> WANDERING_EYE = SkillCapability.flag("speech_wandering_eye");

    /** Summed incoming-damage reduction (0-1) for a holder's tamed pets (Pack Leader). */
    public static final SkillCapability<Double> PACK_LEADER_REDUCTION = SkillCapability.additive("speech_pack_leader_reduction");
    /** Per-nearby-pet outgoing-damage bonus shared by a holder and their pets (Kindred Fury). */
    public static final SkillCapability<Double> KINDRED_FURY = SkillCapability.additive("speech_kindred_fury");
    /** Fraction (0-1) of the post-breeding cooldown removed when the holder bred the pair (Husbandry). */
    public static final SkillCapability<Double> HUSBANDRY = SkillCapability.additive("speech_husbandry");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.SPEECH, Component.translatable("chronicles_leveling.skill.speech"))
                .description(Component.translatable("chronicles_leveling.skill.speech.desc"))

                // Root: trade-core. Cheaper trades scale with spent rank (a per-player price diff).
                .perk("haggler").cost(1).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(TRADE_DISCOUNT, Configs.SKILLS.speech.tradeDiscountPerRank.get() * rank)))

                // Trade branch.
                .perk("reputation").requires("haggler").cost(1)
                    .effect(grant(BETTER_RESTOCK, Boolean.TRUE))
                .perk("master_negotiator").requires("reputation").cost(3)
                    .effect(grant(VILLAGER_XP_BONUS, Configs.SKILLS.speech.villagerXpBonus.get()))
                .perk("silver_tongue").requires("haggler").cost(3).order(10).maxRank(2)
                    .effectsAtRank(rank -> List.of(grant(NO_STOCK_CONSUME, Configs.SKILLS.speech.noStockConsumeChance.get() * rank)))
                .perk("enchanted_trader").requires("silver_tongue").cost(3).maxRank(2)
                    .effectsAtRank(rank -> List.of(grant(ENCHANTED_TRADER, (double) rank)))
                .perk("power_broker").requires("enchanted_trader").cost(3).maxRank(2).order(5)
                    .effectsAtRank(rank -> List.of(grant(ENCHANT_LEVEL_BOOST, (double) rank)))
                .perk("wandering_eye").requires("reputation").cost(3).order(20)
                    .effect(grant(WANDERING_EYE, Boolean.TRUE))

                // Taming branch: Beast Whisperer active gates Husbandry + the Pack Leader line into the Pacify capstone.
                .perk("beast_whisperer").requires("haggler").cost(3).order(30)
                    .effect(unlocks(BeastWhispererAbility.ID))
                .ability(new BeastWhispererAbility())
                .perk("husbandry").requires("beast_whisperer").cost(1).order(10)
                    .effect(grant(HUSBANDRY, Configs.SKILLS.speech.husbandryCooldownReduction.get()))
                .perk("pack_leader").requires("beast_whisperer").cost(2).order(20).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(PACK_LEADER_REDUCTION, Configs.SKILLS.speech.packLeaderReductionPerRank.get() * rank)))
                .perk("kindred_fury").requires("pack_leader").cost(3).order(10)
                    .effect(grant(KINDRED_FURY, Configs.SKILLS.speech.kindredFuryPerPet.get()))
                .perk("pacify").requires("pack_leader").cost(3).order(20).maxRank(3)
                    .effect(unlocks(PacifyAbility.ID))
                .ability(new PacifyAbility())

                .build();
    }
}
