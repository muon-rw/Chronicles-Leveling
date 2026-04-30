package dev.muon.chronicles_leveling.config.skill;

import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;

import java.util.Set;

/**
 * Acrobatics skill config. Two trickle-in sources:
 * <ul>
 *   <li>Jumping — a tiny per-jump grant. Held flat (not formula-driven) so
 *       trainers can't be gamed by jumping into infinite XP via expressions
 *       referencing player state.</li>
 *   <li>Pre-mitigation fall damage — same shape as {@link ArmorSkillConfig};
 *       the routing layer awards both armor and acrobatics XP from a single
 *       fall hit.</li>
 * </ul>
 */
public class AcrobaticsSkillConfig extends SkillConfig {

    @Comment("XP awarded per jump. Tiny on purpose — pure idle activity.")
    public ValidatedDouble xpPerJump = new ValidatedDouble(0.05, 100.0, 0.0);

    @Comment("XP awarded per point of fall damage taken (pre-mitigation). 'd' = damage.")
    public ValidatedExpression xpPerFallDamage = new ValidatedExpression("d", Set.of('d'));

    public AcrobaticsSkillConfig() {
        super(Skills.ACROBATICS);
    }
}
