package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.config.ConfigStats;
import dev.muon.chronicles_leveling.config.Configs;

import java.util.Map;

/**
 * XP-cost-to-next-level math. Driven by a single configurable EvalEx-style
 * expression on {@link ConfigStats#xpCurveExpression}
 * with {@code l} bound to the current level. Synced, so server ops can retune
 * the curve without a restart.
 *
 * <p>Failure modes (parse error, evaluation error, NaN, negative) all clamp to
 * {@code 1} so the player can never get stuck on a 0-cost rung.
 */
public final class LevelingCurve {

    private LevelingCurve() {}

    private static final double FALLBACK_COST = 50.0;

    public static int xpToNext(int level) {
        if (level < 1) level = 1;
        double cost = Configs.STATS.xpCurveExpression.evalSafe(
                Map.of('l', (double) level), FALLBACK_COST);
        return (int) Math.max(1, Math.round(cost));
    }
}
