package dev.muon.chronicles_leveling.config.skill;

import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;

import java.util.Set;

/**
 * Armor skill config. XP gain comes from damage taken — measured before any
 * armor or resistance reduction so a heavily-armored player still trains the
 * skill at the same rate as an unarmored one (otherwise the skill stalls
 * exactly when it starts working).
 */
public class ArmorSkillConfig extends SkillConfig {

    @Comment("XP awarded per hit taken. 'd' = damage amount, pre-mitigation.")
    public ValidatedExpression xpPerDamageTaken = new ValidatedExpression("d", Set.of('d'));

    public ArmorSkillConfig() {
        super(Skills.ARMOR);
    }
}
