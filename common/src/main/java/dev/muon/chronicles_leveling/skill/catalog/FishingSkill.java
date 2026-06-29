package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Magnitude.flat;
import static dev.muon.chronicles_leveling.skill.perk.Perks.attr;
import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

public final class FishingSkill {

    private FishingSkill() {}

    /** Multiplier to bobber bite-wait reduction (clamp at the read site). */
    public static final SkillCapability<Double> BITE_SPEED = SkillCapability.additive("fishing_bite_speed");
    /** Extra blocks the lure attracts/widens the catch range. */
    public static final SkillCapability<Double> LURE_RANGE = SkillCapability.additive("fishing_lure_range");
    /** Summed chance for an extra/upgraded treasure-tier catch. */
    public static final SkillCapability<Double> TREASURE_BONUS = SkillCapability.additive("fishing_treasure_bonus");
    /** Summed chance a catch comes pre-enchanted. */
    public static final SkillCapability<Double> ENCHANTED_CATCH = SkillCapability.additive("fishing_enchanted_catch");
    /** Summed chance to reel in a second item (double catch). */
    public static final SkillCapability<Double> DOUBLE_CATCH = SkillCapability.additive("fishing_double_catch");
    /** Caught fish restore more hunger and are safe to eat raw. */
    public static final SkillCapability<Boolean> FISHER_FEAST = SkillCapability.flag("fishing_fisher_feast");
    /** Rod/trident hits reel the struck mob toward you. */
    public static final SkillCapability<Boolean> HARPOON_REEL = SkillCapability.flag("fishing_harpoon_reel");
    /** Faster trident Riptide/Loyalty return (clamp at the read site). */
    public static final SkillCapability<Double> TRIDENT_RETURN_SPEED = SkillCapability.additive("fishing_trident_return_speed");
    /** Bobber can be cast through / break frozen water. */
    public static final SkillCapability<Boolean> FROSTBREAKER = SkillCapability.flag("fishing_frostbreaker");

    private static final Identifier LUCK = Identifier.withDefaultNamespace("luck");
    private static final Identifier RANGED_DAMAGE = Identifier.fromNamespaceAndPath("combat_attributes", "ranged_damage");
    private static final Identifier ARROW_VELOCITY = Identifier.fromNamespaceAndPath("combat_attributes", "arrow_velocity");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.FISHING, Component.translatable("chronicles_leveling.skill.fishing"))
                .description(Component.translatable("chronicles_leveling.skill.fishing.desc"))

                // Root: bites come faster and the lure reaches wider (behavior capabilities;
                // the bobber handler scales BITE_SPEED against skill level at the read site.
                // Magnitude only drives attr() effects, so the grant carries a flat tuning base).
                .perk("patient_angler").cost(4)
                    .effect(grant(BITE_SPEED, Configs.SKILLS.fishing.biteSpeedBonus.get()))
                    .effect(grant(LURE_RANGE, Configs.SKILLS.fishing.lureRangeBonus.get()))

                // Loot branch: treasure quality, double catch, pre-enchants.
                .perk("fortunes_catch").requires("patient_angler").cost(3).maxRank(3)
                    .effectsAtRank(rank -> List.of(
                            grant(TREASURE_BONUS, Configs.SKILLS.fishing.treasureBonusPerRank.get() * rank),
                            attr(LUCK, ADD_VALUE, flat(Configs.SKILLS.fishing.luckBonus.get()))))
                .perk("big_catch").requires("fortunes_catch").cost(7).order(10)
                    .effect(grant(DOUBLE_CATCH, Configs.SKILLS.fishing.doubleCatchChance.get()))
                .perk("enchanted_catch").requires("fortunes_catch").cost(7).order(20)
                    .effect(grant(ENCHANTED_CATCH, Configs.SKILLS.fishing.enchantedCatchChance.get()))

                // Sustenance branch: caught fish feed you better.
                .perk("fishers_feast").requires("patient_angler").cost(4).order(30)
                    .effect(grant(FISHER_FEAST, Boolean.TRUE))

                // Combat / trident branch: ties Fishing into the combat layer.
                .perk("harpoon").requires("patient_angler").cost(7).order(40)
                    .effect(grant(HARPOON_REEL, Boolean.TRUE))
                    .effect(attr(RANGED_DAMAGE, ADD_VALUE, flat(Configs.SKILLS.fishing.harpoonRangedDamage.get())))
                .perk("trident_master").requires("harpoon").cost(7)
                    .effect(grant(TRIDENT_RETURN_SPEED, Configs.SKILLS.fishing.tridentReturnSpeed.get()))
                    .effect(attr(ARROW_VELOCITY, ADD_MULTIPLIED_BASE, flat(Configs.SKILLS.fishing.tridentVelocityBonus.get())))

                // Utility: fish through ice.
                .perk("frostbreaker").requires("patient_angler").cost(4).order(50)
                    .effect(grant(FROSTBREAKER, Boolean.TRUE))

                .build();
    }
}
