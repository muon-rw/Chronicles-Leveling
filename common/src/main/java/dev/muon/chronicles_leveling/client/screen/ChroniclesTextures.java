package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.resources.Identifier;

/**
 * Texture identifiers for Chronicles' GUI. The art is reused from PlayerEx, licensed MIT
 *
 * <p>Layout assumptions baked into UV coordinates elsewhere:
 * <ul>
 *   <li>{@link #TAB} / {@link #TAB_DARK} — 256×256, a 4×6 grid of 28×32 tab cells.
 *       Row 0 = top-row active, row 1 = top-row inactive,
 *       row 2 = bottom-row active, row 3 = bottom-row inactive.</li>
 *   <li>{@link #GUI} / {@link #GUI_DARK} — 256×256 inventory-style frame with a
 *       transparent center, 176×166 anchored at (0, 0).</li>
 *   <li>{@link #PARCHMENT} — 256×256 paper backdrop blitted under the frame.</li>
 *   <li>{@link #ICON_INVENTORY} etc. — 16×16, blitted at full size onto each
 *       tab face.</li>
 * </ul>
 */
public final class ChroniclesTextures {

    private ChroniclesTextures() {}

    private static Identifier gui(String path) {
        return ChroniclesLeveling.id("textures/gui/" + path + ".png");
    }

    public static final Identifier TAB = gui("tab");
    public static final Identifier TAB_DARK = gui("tab_dark");

    public static final Identifier GUI = gui("gui");
    public static final Identifier GUI_DARK = gui("gui_dark");
    public static final Identifier PARCHMENT = gui("parchment");

    public static final Identifier ICON_INVENTORY = gui("inventory");
    public static final Identifier ICON_COMBAT = gui("combat");
    public static final Identifier ICON_ATTRIBUTES = gui("attributes");
    public static final Identifier ICON_BLANK = gui("blank");
}
