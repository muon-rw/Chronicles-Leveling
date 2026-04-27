package dev.muon.chronicles_leveling.client.screen;

import net.minecraft.network.chat.Component;

/**
 * Shared "Progress: x/y (n.n%)" tooltip + hit-test for the XP bars on the
 * Levels and Skills screens. Centralised so the format string, K/M/B/T number
 * abbreviation, and hover-zone padding stay consistent across both screens.
 */
public final class ProgressTooltip {

    /**
     * Pixels added to each side of a bar's bounding box for hover detection.
     * The bars are very thin (3–5px), so naked bounds make the tooltip almost
     * unhittable; padding gives a forgiving 5–7px tall hit zone.
     */
    public static final int HOVER_PADDING = 1;

    private ProgressTooltip() {}

    /** Builds the "Progress: x/y (n.n%)" component for a given xp / xp-to-next pair. */
    public static Component build(int xp, int xpToNext) {
        double percent = xpToNext > 0 ? (100.0 * xp) / xpToNext : 0.0;
        return Component.translatable(
                "chronicles_leveling.tooltip.progress",
                formatAmount(xp),
                formatAmount(xpToNext),
                String.format("%.1f", percent));
    }

    /**
     * Whether the cursor is over a bar at {@code (barX, barY)} of size
     * {@code (barW, barH)}, expanded by {@link #HOVER_PADDING} pixels in every direction.
     */
    public static boolean isHovered(int mouseX, int mouseY, int barX, int barY, int barW, int barH) {
        return mouseX >= barX - HOVER_PADDING
                && mouseX < barX + barW + HOVER_PADDING
                && mouseY >= barY - HOVER_PADDING
                && mouseY < barY + barH + HOVER_PADDING;
    }

    /** Formats counts of 1000+ with one decimal and a K/M/B/T suffix; values under 1000 render as-is. */
    public static String formatAmount(long value) {
        if (value < 1000) return Long.toString(value);
        String[] suffixes = {"K", "M", "B", "T"};
        double scaled = value / 1000.0;
        int idx = 0;
        while (scaled >= 1000 && idx < suffixes.length - 1) {
            scaled /= 1000;
            idx++;
        }
        return String.format("%.1f%s", scaled, suffixes[idx]);
    }
}
