package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.config.Configs;

/**
 * XP-to-next-level math. Uses {@code base + slope * (level - 1)^exponent},
 * with all three values driven from {@link dev.muon.chronicles_leveling.config.ConfigSync}
 * so server ops can retune the curve without a restart.
 *
 * <p>Kept as a single static helper rather than a polymorphic strategy because
 * everything we need is captured in three numbers; if a strategy is ever
 * needed, callers should swap this whole class out, not subclass it.
 */
public final class LevelingCurve {

    private LevelingCurve() {}

    /**
     * XP required to advance from {@code level} → {@code level + 1}.
     * Always returns at least 1 so the player can never get stuck on a 0-cost rung.
     */
    public static int xpToNext(int level) {
        if (level < 1) level = 1;
        double base = Configs.SYNC.xpCurveBase.get();
        double slope = Configs.SYNC.xpCurveSlope.get();
        double exponent = Configs.SYNC.xpCurveExponent.get();
        double rung = base + slope * Math.pow(level - 1, exponent);
        return (int) Math.max(1, Math.round(rung));
    }
}
