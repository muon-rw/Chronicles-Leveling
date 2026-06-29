package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.resources.Identifier;

/**
 * Texture identifiers for Chronicles' GUI. Some art is reused from PlayerEx, licensed MIT
 *
 * <p>Layout assumptions baked into UV coordinates elsewhere:
 * <ul>
 *   <li>{@link #TAB} / {@link #TAB_DARK}: 256×256, a 4×6 grid of 28×32 tab cells.
 *       Row 0 = top-row active, row 1 = top-row inactive,
 *       row 2 = bottom-row active, row 3 = bottom-row inactive.</li>
 *   <li>{@link #GUI} / {@link #GUI_DARK}: 256×256 inventory-style frame with a
 *       transparent center, 176×166 anchored at (0, 0).</li>
 *   <li>{@link #PARCHMENT}: 256×256 paper backdrop blitted under the frame.</li>
 *   <li>{@link #ICON_INVENTORY} etc.: 16×16, blitted at full size onto each
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

    /** 384×128 forest-clearing backdrop drawn behind a skill tree (pans with the tree, centered on it). */
    public static final Identifier CLEARING = gui("clearing");

    /** The fixed 404×192 skill-tree pop-out panel (header / 384×128 clearing slot / footer / 10px margins). */
    public static final Identifier SKILL_TREE_PANEL = gui("skill_tree_panel");

    /** Perk-node frames (26×26, 2px border, 22px transparent center): owned (selected) vs not. */
    public static final Identifier NODE_FRAME_SELECTED = gui("node_frame_selected");
    public static final Identifier NODE_FRAME_UNSELECTED = gui("node_frame_unselected");

    /** HUD ability-slot frame (22×22, 2px border, 18px transparent center), blitted scaled to the slot size. */
    public static final Identifier ABILITY_SLOT_FRAME = gui("ability_slot_frame");

    public static final Identifier ICON_INVENTORY = gui("inventory");
    public static final Identifier ICON_COMBAT = gui("combat");
    public static final Identifier ICON_LEVELS = gui("levels");
    public static final Identifier ICON_PROFESSIONS = gui("professions");
    public static final Identifier ICON_BLANK = gui("blank");

    /** A perk node's icon: {@code textures/gui/perk/<skill>/<perk>.png}, keyed by ({@code owningSkill}, {@code perkId}). */
    public static Identifier perk(String owningSkill, String perkId) {
        return gui("perk/" + owningSkill + "/" + perkId);
    }

    /** The desaturated "locked" variant of a perk icon: {@code textures/gui/perk_locked/<skill>/<perk>.png}. */
    public static Identifier perkLocked(String owningSkill, String perkId) {
        return gui("perk_locked/" + owningSkill + "/" + perkId);
    }
}
