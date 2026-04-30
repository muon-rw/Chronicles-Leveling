package dev.muon.chronicles_leveling.config.skill;

import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;

import java.util.Set;

/**
 * Magic skill config. XP gain comes from magic damage dealt — anything tagged
 * {@code #c:is_magic} on the damage source. Mirrors the Combat-Attributes
 * convention so spell mods don't need new wiring.
 */
public class MagicSkillConfig extends SkillConfig {

    @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
    public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));

    public MagicSkillConfig() {
        super(Skills.MAGIC);
    }
}
