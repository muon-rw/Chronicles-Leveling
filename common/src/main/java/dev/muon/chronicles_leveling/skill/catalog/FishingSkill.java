package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.perk.PerkEffect;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Magnitude.flat;
import static dev.muon.chronicles_leveling.skill.perk.Perks.attr;
import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

public final class FishingSkill {

    private FishingSkill() {}

    /** Fraction the bobber bite-wait is reduced by (clamp at the read site). */
    public static final SkillCapability<Double> BITE_SPEED = SkillCapability.additive("fishing_bite_speed");
    /** Summed chance for an extra/upgraded treasure-tier catch. */
    public static final SkillCapability<Double> TREASURE_BONUS = SkillCapability.additive("fishing_treasure_bonus");
    /** Number of independent extra-copy rolls a catch gets (Big Catch rank). */
    public static final SkillCapability<Double> MULTI_CATCH_ROLLS = SkillCapability.additive("fishing_multi_catch_rolls");
    /** Extra random enchantments rolled onto fished-up gear (Enchanted Catch rank). */
    public static final SkillCapability<Double> FISHED_EXTRA_ENCHANTS = SkillCapability.additive("fishing_extra_enchants");
    /** Levels added to every enchantment on fished-up gear, clamped to each enchantment's max (Leviathan's Gift rank). */
    public static final SkillCapability<Double> FISHED_ENCHANT_LEVEL_BOOST = SkillCapability.additive("fishing_enchant_level_boost");
    /** Junk is rerolled out of the catch. */
    public static final SkillCapability<Boolean> NO_JUNK = SkillCapability.flag("fishing_no_junk");
    /** Caught fish restore more hunger and are safe to eat raw. */
    public static final SkillCapability<Boolean> FISHER_FEAST = SkillCapability.flag("fishing_fisher_feast");
    /** Rod/trident hits reel the struck mob toward you. */
    public static final SkillCapability<Boolean> HARPOON_REEL = SkillCapability.flag("fishing_harpoon_reel");
    /** Faster trident Loyalty return (clamp at the read site); Worthy rank 1. */
    public static final SkillCapability<Double> TRIDENT_RETURN_SPEED = SkillCapability.additive("fishing_trident_return_speed");
    /** Thrown trident snaps straight back to its owner; Worthy rank 2+. */
    public static final SkillCapability<Boolean> TRIDENT_INSTANT_RETURN = SkillCapability.flag("fishing_trident_instant_return");
    /** Thrown trident returns even without Loyalty and survives the void; Worthy rank 3. */
    public static final SkillCapability<Boolean> TRIDENT_AUTO_RETURN = SkillCapability.flag("fishing_trident_auto_return");
    /** Bobber can be cast through / break frozen water. */
    public static final SkillCapability<Boolean> FROSTBREAKER = SkillCapability.flag("fishing_frostbreaker");
    /** Channelling tridents strike lightning in any rain, not just thunderstorms; Storm God rank 1. */
    public static final SkillCapability<Boolean> STORM_RAIN_CHANNEL = SkillCapability.flag("fishing_storm_rain_channel");
    /** Channelling tridents always strike lightning on hit (under open sky); Storm God rank 2+. */
    public static final SkillCapability<Boolean> STORM_ALWAYS_LIGHTNING = SkillCapability.flag("fishing_storm_always_lightning");
    /** Riptide works anywhere, no water or rain needed; Storm God rank 3. */
    public static final SkillCapability<Boolean> STORM_RIPTIDE = SkillCapability.flag("fishing_storm_riptide");

    private static final Identifier LUCK = Identifier.withDefaultNamespace("luck");
    private static final Identifier RANGED_DAMAGE = Identifier.fromNamespaceAndPath("combat_attributes", "ranged_damage");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.FISHING, Component.translatable("chronicles_leveling.skill.fishing"))
                .description(Component.translatable("chronicles_leveling.skill.fishing.desc"))

                // Root: bites come faster each rank (20% / 40% / 60%); the bobber handler scales the bite wait
                // by this fraction at the read site.
                .perk("patient_angler").cost(1).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(BITE_SPEED, Configs.SKILLS.fishing.biteSpeedPerRank.get() * rank)))

                // Loot branch: treasure quality, multi-catch, enchanted gear.
                .perk("fortunes_catch").requires("patient_angler").cost(2).maxRank(3)
                    .effectsAtRank(rank -> List.of(
                            grant(TREASURE_BONUS, Configs.SKILLS.fishing.treasureBonusPerRank.get() * rank),
                            attr(LUCK, ADD_VALUE, flat(Configs.SKILLS.fishing.luckBonus.get()))))
                .perk("big_catch").requires("fortunes_catch").cost(2).maxRank(3).order(10)
                    .effectsAtRank(rank -> List.of(grant(MULTI_CATCH_ROLLS, (double) rank)))
                .perk("enchanted_catch").requires("fortunes_catch").cost(2).maxRank(2).order(20)
                    .effectsAtRank(rank -> List.of(grant(FISHED_EXTRA_ENCHANTS, (double) rank)))
                .perk("leviathans_gift").requires("enchanted_catch").cost(2).maxRank(2).order(25)
                    .effectsAtRank(rank -> List.of(grant(FISHED_ENCHANT_LEVEL_BOOST, (double) rank)))

                // Utility: never reel junk.
                .perk("discerning_fisher").requires("patient_angler").cost(3).order(28)
                    .effect(grant(NO_JUNK, Boolean.TRUE))

                // Sustenance branch: caught fish feed you better.
                .perk("fishers_feast").requires("patient_angler").cost(3).order(30)
                    .effect(grant(FISHER_FEAST, Boolean.TRUE))

                // Combat / trident branch: ties Fishing into the combat layer.
                .perk("harpoon").requires("patient_angler").cost(3).order(40)
                    .effect(grant(HARPOON_REEL, Boolean.TRUE))
                    .effect(attr(RANGED_DAMAGE, ADD_VALUE, flat(Configs.SKILLS.fishing.harpoonRangedDamage.get())))
                .perk("worthy").requires("harpoon").cost(2).maxRank(3).order(45)
                    .effectsAtRank(FishingSkill::worthyEffects)
                .perk("storm_god").requires("worthy").cost(3).maxRank(3).order(50)
                    .effectsAtRank(FishingSkill::stormGodEffects)

                // Utility: fish through ice.
                .perk("frostbreaker").requires("patient_angler").cost(3).order(60)
                    .effect(grant(FROSTBREAKER, Boolean.TRUE))

                .build();
    }

    // Worthy: r1 faster Loyalty return, r2 instant return, r3 instant + return-on-miss (and void immunity, read off AUTO_RETURN).
    private static List<PerkEffect> worthyEffects(int rank) {
        if (rank >= 3) {
            return List.of(grant(TRIDENT_INSTANT_RETURN, Boolean.TRUE), grant(TRIDENT_AUTO_RETURN, Boolean.TRUE));
        }
        if (rank == 2) {
            return List.of(grant(TRIDENT_INSTANT_RETURN, Boolean.TRUE));
        }
        return List.of(grant(TRIDENT_RETURN_SPEED, Configs.SKILLS.fishing.tridentReturnSpeed.get()));
    }

    // Storm God: r1 Channelling in any rain, r2 always strikes lightning, r3 always + Riptide anywhere.
    private static List<PerkEffect> stormGodEffects(int rank) {
        if (rank >= 3) {
            return List.of(grant(STORM_ALWAYS_LIGHTNING, Boolean.TRUE), grant(STORM_RIPTIDE, Boolean.TRUE));
        }
        if (rank == 2) {
            return List.of(grant(STORM_ALWAYS_LIGHTNING, Boolean.TRUE));
        }
        return List.of(grant(STORM_RAIN_CHANNEL, Boolean.TRUE));
    }
}
