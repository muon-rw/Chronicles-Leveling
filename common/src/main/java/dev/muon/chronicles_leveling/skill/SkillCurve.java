package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.config.skill.SkillConfig;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;

import java.util.Map;

/**
 * XP-cost-to-next-level math, per skill. Each skill carries its own EvalEx-style
 * expression on its synced {@link SkillConfig}; {@code l} is bound to the
 * current skill level when evaluated.
 *
 * <p>Synced (each skill config is registered as
 * {@link me.fzzyhmstrs.fzzy_config.api.RegisterType#BOTH}), so server ops can
 * retune curves without a restart and clients see the right progress bar fill.
 *
 * <p>Failure modes (parse error, evaluation error, NaN, negative, unknown
 * skill) all clamp to {@code 1} so the bar is never divided by zero.
 */
public final class SkillCurve {

    private SkillCurve() {}

    private static final double FALLBACK_COST = 100.0;

    public static int xpToNext(String skillId, int level) {
        if (level < 1) level = 1;
        SkillConfig cfg = Configs.skill(skillId);
        if (cfg == null) return Math.max(1, (int) Math.round(FALLBACK_COST));
        ValidatedExpression expr = cfg.xpCurve;
        double cost = expr.evalSafe(Map.of('l', (double) level), FALLBACK_COST);
        return (int) Math.max(1, Math.round(cost));
    }
}
