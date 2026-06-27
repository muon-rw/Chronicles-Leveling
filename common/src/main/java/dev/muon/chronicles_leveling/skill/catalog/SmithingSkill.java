package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;

/**
 * Smithing skill tree: SHELVED pending an external mod (see docs/skills-perks-design.md "Smithing"). Same
 * situation as {@link MagicSkill}: the interesting perks (masterwork/affix rolls, set bonuses, salvage/tinker)
 * presuppose a gear-quality / affix pipeline we've decided to lean on an external dependency for rather than
 * reinvent in-house. This registers an empty but valid tree so {@code Skills.SMITHING} survives
 * freeze-validation; Smithing XP still accrues from crafting via {@code SmithingXpHandler}. Populate
 * {@code define()} once a smithing dependency lands.
 */
public final class SmithingSkill {

    private SmithingSkill() {}

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.SMITHING, Component.translatable("chronicles_leveling.skill.smithing"))
                .description(Component.translatable("chronicles_leveling.skill.smithing.desc"))
                .build();
    }
}
