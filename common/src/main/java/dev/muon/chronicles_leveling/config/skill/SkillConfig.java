package dev.muon.chronicles_leveling.config.skill;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Base config for a single skill. One file per skill under
 * {@code config/chronicles_leveling/skills/<skill>.toml}, registered as a
 * synced config so server-side curve edits show up in client progress bars.
 *
 * <p>Subclasses add skill-specific fields (xp-from-damage formulas, brewing
 * tables, etc.) via additional public fields — FzzyConfig walks the class
 * with reflection and picks up inherited fields too.
 */
public class SkillConfig extends Config {

    public static final String DEFAULT_CURVE = "100 + 25 * (l - 1)^1.5";

    @Comment("XP required to advance from level l to l+1. 'l' = current skill level.")
    public ValidatedExpression xpCurve = new ValidatedExpression(DEFAULT_CURVE, Set.of('l'));

    public SkillConfig(String skillId) {
        super(Identifier.fromNamespaceAndPath(ChroniclesLeveling.MOD_ID, skillId), "skills");
    }
}
