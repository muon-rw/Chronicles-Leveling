package dev.muon.chronicles_leveling.config.skill;

import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;

import java.util.Set;

/**
 * Archery skill config. XP gain comes from non-magic projectile damage dealt
 * — anything where the {@link net.minecraft.world.damagesource.DamageSource}
 * is in {@code minecraft:is_projectile} and not {@code c:is_magic}.
 */
public class ArcherySkillConfig extends SkillConfig {

    @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
    public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));

    public ArcherySkillConfig() {
        super(Skills.ARCHERY);
    }
}
