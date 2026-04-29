package dev.muon.chronicles_leveling.level;

import net.minecraft.world.entity.player.Player;

/**
 * Helpers around the player's vanilla XP economy. Spendable XP is derived from
 * {@code experienceLevel + experienceProgress} because {@link
 * Player#totalExperience} tracks lifetime XP gained — it doesn't decrement when
 * levels are spent on enchants or anvils (except a strange case where it
 * could get set to 0)
 */
public final class VanillaXp {

    private VanillaXp() {}

    /**
     * Vanilla XP cost to advance from {@code fromLevel} to {@code fromLevel + 1}.
     * Mirrors {@link Player#getXpNeededForNextLevel()}; duplicated because that
     * method reads {@code this.experienceLevel} and has no static counterpart.
     */
    public static int getXpNeededForNextVanillaLevel(int fromLevel) {
        if (fromLevel >= 30) return 112 + (fromLevel - 30) * 9;
        if (fromLevel >= 15) return 37 + (fromLevel - 15) * 5;
        return 7 + fromLevel * 2;
    }

    /**
     * The player's currently-spendable XP — the sum of every level's worth of XP
     * they've banked plus the partial progress toward the next level. Loop is
     * bounded by {@code experienceLevel} (typically &lt; 100), so closed-form
     * isn't worth the readability cost.
     */
    public static int availableExperiencePoints(Player player) {
        int level = Math.max(0, player.experienceLevel);
        long total = 0;
        for (int l = 0; l < level; l++) {
            total += getXpNeededForNextVanillaLevel(l);
            if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        total += (long) Math.floor(player.experienceProgress * player.getXpNeededForNextLevel());
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * The vanilla XP level corresponding to a banked total of {@code xp} spendable
     * XP — the inverse of {@link #availableExperiencePoints(Player)}. Walks forward through the
     * piecewise XP curve until the next rung would overshoot. Used to convert a
     * mod-XP cost into "how many vanilla levels does this represent".
     */
    public static int getLevelForTotalXp(int xp) {
        if (xp <= 0) return 0;
        int level = 0;
        long remaining = xp;
        while (level < 1_000_000) {
            int cost = getXpNeededForNextVanillaLevel(level);
            if (remaining < cost) return level;
            remaining -= cost;
            level++;
        }
        return level;
    }
}
