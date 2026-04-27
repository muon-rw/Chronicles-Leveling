package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.stat.ModStats;

/**
 * UV constants for the sprite atlas baked into the top-right of {@link
 * ChroniclesTextures#GUI}. The 256×256 sheet packs two grids:
 *
 * <ul>
 *   <li>Buttons — 11×10 cells in a 3×2 grid at {@code x=204..225, y=0..29}.
 *       Three rows = idle / hover / disabled states.</li>
 *   <li>Icons — 9×9 cells in a 5×3 grid at {@code x=226..252, y=0..44}.</li>
 * </ul>
 *
 * <p>Cells sit flush with no inter-cell gap; some glyphs (notably the {@code +}
 * icon) fill the entire 9px width, others leave a transparent margin.
 */
public final class ChroniclesSprites {

    private ChroniclesSprites() {}

    public static final int SHEET_SIZE = 256;

    public static final int BUTTON_W = 11;
    public static final int BUTTON_H = 10;

    private static final int BUTTON_PLUS_X = 204;
    private static final int BUTTON_MINUS_X = 215;

    private static final int BUTTON_ROW_IDLE = 0;
    private static final int BUTTON_ROW_HOVER = 10;
    private static final int BUTTON_ROW_DISABLED = 20;

    public static int plusButtonU() { return BUTTON_PLUS_X; }
    public static int minusButtonU() { return BUTTON_MINUS_X; }

    public static int buttonV(boolean active, boolean hovered) {
        if (!active) return BUTTON_ROW_DISABLED;
        return hovered ? BUTTON_ROW_HOVER : BUTTON_ROW_IDLE;
    }

    public static final int ICON_SIZE = 9;

    private static final int ICON_COL1 = 226;
    private static final int ICON_COL2 = 235;
    private static final int ICON_COL3 = 244;

    private static final int ICON_ROW1 = 0;
    private static final int ICON_ROW2 = 9;
    private static final int ICON_ROW3 = 18;
    private static final int ICON_ROW4 = 27;
    private static final int ICON_ROW5 = 36;

    public static final int XP_U = ICON_COL1, XP_V = ICON_ROW1;
    public static final int SPEED_U = ICON_COL2, SPEED_V = ICON_ROW1;
    public static final int ICON_PLUS_U = ICON_COL3, ICON_PLUS_V = ICON_ROW1;

    public static final int HEART_U = ICON_COL1, HEART_V = ICON_ROW2;
    public static final int BIGPLUS_U = ICON_COL2, BIGPLUS_V = ICON_ROW2;
    public static final int SWORD_U = ICON_COL3, SWORD_V = ICON_ROW2;

    public static final int SHIELD_U = ICON_COL1, SHIELD_V = ICON_ROW3;
    public static final int BOW_U = ICON_COL2, BOW_V = ICON_ROW3;
    public static final int LIFESTEAL_U = ICON_COL3, LIFESTEAL_V = ICON_ROW3;

    public static final int ICON_GEAR_U = ICON_COL1, ICON_GEAR_V = ICON_ROW4;
    public static final int MOON_U = ICON_COL2, MOON_V = ICON_ROW4;
    public static final int COIN_U = ICON_COL3, COIN_V = ICON_ROW4;

    public static final int SKULL_U = ICON_COL1, SKULL_V = ICON_ROW5;
    public static final int PICKAXE_U = ICON_COL2, PICKAXE_V = ICON_ROW5;

    public record IconCoord(int u, int v) {}

    /** Icon for the row that names the player's current level (the "skillable" buy-XP row). */
    public static final IconCoord LEVEL_ICON = new IconCoord(XP_U, XP_V);

    /**
     * Icon for a given stat id. Falls back to the level icon for unknown ids
     * so a misconfigured row still draws something visible rather than crashing.
     */
    public static IconCoord iconForStat(String statId) {
        return switch (statId) {
            case ModStats.STRENGTH     -> new IconCoord(SWORD_U, SWORD_V);
            case ModStats.DEXTERITY    -> new IconCoord(BOW_U, BOW_V);
            case ModStats.CONSTITUTION -> new IconCoord(HEART_U, HEART_V);
            case ModStats.INTELLIGENCE -> new IconCoord(MOON_U, MOON_V);
            case ModStats.WISDOM       -> new IconCoord(BIGPLUS_U, BIGPLUS_V);
            case ModStats.LUCKINESS    -> new IconCoord(COIN_U, COIN_V);
            default                    -> LEVEL_ICON;
        };
    }
}
