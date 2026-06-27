package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;

/**
 * Magic skill tree: SHELVED (see docs/skills-perks-design.md "Magic"). No spell system exists yet, so a perk
 * would have nothing to hook into. This registers an empty but valid tree so {@code Skills.MAGIC} survives
 * freeze-validation; populate {@code define()} once a spell pipeline lands.
 */
public final class MagicSkill {

    private MagicSkill() {}

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.MAGIC, Component.translatable("chronicles_leveling.skill.magic"))
                .description(Component.translatable("chronicles_leveling.skill.magic.desc"))
                .build();
    }
}
