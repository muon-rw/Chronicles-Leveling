package dev.muon.chronicles_leveling.config.skill;

import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;

import java.util.Set;

/**
 * Weaponry skill config. XP gain comes from non-magic melee damage dealt to
 * other entities — the formula receives {@code d} = damage amount (the value
 * passed into the hurt pipeline, before the victim's armor/resistances reduce
 * it).
 */
public class WeaponrySkillConfig extends SkillConfig {

    @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
    public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));

    public WeaponrySkillConfig() {
        super(Skills.WEAPONRY);
    }
}
