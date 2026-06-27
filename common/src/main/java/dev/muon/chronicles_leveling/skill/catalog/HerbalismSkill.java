package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.BountifulHarvestAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static dev.muon.chronicles_leveling.skill.perk.Perks.unlocks;

public final class HerbalismSkill {

    private HerbalismSkill() {}

    /** Summed chance (clamp at the read site) that a harvested crop drops extra yield. */
    public static final SkillCapability<Double> EXTRA_CROP_YIELD = SkillCapability.additive("herbalism_extra_crop_yield");
    /** Harvesting a mature crop replants it, spending one of the crop's seeds from the player's inventory. */
    public static final SkillCapability<Boolean> AUTO_REPLANT = SkillCapability.flag("herbalism_auto_replant");
    /** Summed chance that breaking foliage (grass/leaves/etc.) yields a rare valuable. */
    public static final SkillCapability<Double> RUPEE_FARMER = SkillCapability.additive("herbalism_rupee_farmer");
    /** Summed chance that a harvest also yields an alchemy reagent (Alchemy synergy). */
    public static final SkillCapability<Double> TOXIN_HARVEST = SkillCapability.additive("herbalism_toxin_harvest");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.HERBALISM, Component.translatable("chronicles_leveling.skill.herbalism"))
                .description(Component.translatable("chronicles_leveling.skill.herbalism.desc"))

                // Root: harvesting a mature crop replants it, at the cost of a seed (read in GatherProcRouter).
                .perk("auto_replant").cost(2)
                    .effect(grant(AUTO_REPLANT, Boolean.TRUE))

                // Green Thumb: after planting, a level-scaled chance to auto-bonemeal the crop forward by rank stages.
                // Pure rank node; the behavior is rank-read in GatherProcRouter.greenThumbAfterPlant.
                .perk("green_thumb").requires("auto_replant").cost(2).maxRank(3)
                // Gardener's Infusion: bakes a hunger/saturation boost into food crafted/cooked by a holder (rank-read
                // in GardenersInfusionHandler at the craft/cook seams). Pure rank node.
                .perk("gardeners_infusion").requires("green_thumb").cost(3).maxRank(3).order(40)

                // Cultivation: scaling extra-yield loot node.
                .perk("cultivation").requires("auto_replant").cost(2).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(EXTRA_CROP_YIELD, Configs.SKILLS.herbalism.extraCropYieldPerRank.get() * rank)))
                .perk("rupee_farmer").requires("cultivation").cost(3).order(20)
                .effect(grant(RUPEE_FARMER, Configs.SKILLS.herbalism.rupeeFarmerChance.get()))

                // Fungal branch. Mycologist is a 3-rank node read in MycologyHandler / GatherProcRouter (no capability).
                .perk("mycologist").requires("auto_replant").cost(2).maxRank(3).order(30)
                .perk("toxin_harvest").requires("mycologist").cost(3)
                    .effect(grant(TOXIN_HARVEST, Configs.SKILLS.herbalism.toxinHarvestChance.get()))


                // Bountiful Harvest (active capstone): converges any 2 of the three branch finishers; ranks widen the
                // sweep radius (3 -> 5 -> 7 at the default config).
                .perk("bountiful_harvest").requires("gardeners_infusion", "toxin_harvest", "rupee_farmer")
                    .requireAny(2).anchorUnderParents().cost(5).maxRank(3).order(50)
                    .effect(unlocks(BountifulHarvestAbility.ID))
                .ability(new BountifulHarvestAbility())

                .build();
    }
}
