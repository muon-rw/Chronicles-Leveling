package dev.muon.chronicles_leveling.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.muon.chronicles_leveling.client.ChroniclesKeybinds;
import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillCurve;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbilitySlots;
import dev.muon.chronicles_leveling.skill.ability.SkillAbility;
import dev.muon.chronicles_leveling.skill.perk.AbilityUnlock;
import dev.muon.chronicles_leveling.skill.perk.PerkEffect;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import dev.muon.chronicles_leveling.skill.tree.LaidOutTree;
import dev.muon.chronicles_leveling.skill.tree.LayeredLayout;
import dev.muon.chronicles_leveling.skill.tree.SkillTreeLayout;
import dev.muon.chronicles_leveling.skill.tree.TreeGeometry;
import dev.muon.chronicles_leveling.sounds.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * "Skills" tab: two modes in one screen (same tab):
 * <ul>
 *   <li><b>LIST</b> (landing): a scrollable 2-column overview grid of the player's skills (name, level,
 *       XP bar). Hovering a cell highlights it; clicking it drills into that skill's tree.</li>
 *   <li><b>TREE</b>: one skill's prerequisite DAG laid out by {@link LayeredLayout} on a scissor-clipped,
 *       drag-pannable canvas (no zoom). A back arrow returns to the grid. Clicking a buyable node spends a
 *       point (server re-validates); clicking an unlocked ability node cycles its action-bar slot. The
 *       footer carries level, unspent points, a per-skill reset, and the XP bar; hover/pop/pulse/slide
 *       animations are pure client-side feedback.</li>
 * </ul>
 *
 * <p>The grid surfaces the same {@link #skills} list the trees use (non-empty trees, curated order then
 * addon extras), so addon skills appear and the grid scrolls when they overflow.
 *
 * <p>The tree layout pipeline ({@code skill.tree.*}) is pure and player-independent except for
 * {@link SkillTreeLayout#project}, so geometry is computed once per selected skill and per-player node
 * state is re-derived each frame from live (synced) {@link PlayerSkillManager} data; no dirty-flag
 * bookkeeping. Sized 176×166 to line up with the inventory texture.
 */
public class SkillsScreen extends Screen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;

    private static final int PARCHMENT_OFFSET_X = 6;
    private static final int PARCHMENT_OFFSET_Y = 6;

    private static final int TITLE_Y = 12;
    private static final float TITLE_SCALE = 1.2f;
    private static final int HEADER_LINE_Y = 24;

    // Tree-detail header row (← Name): a back arrow at the left returns to the overview grid.
    private static final int CYCLER_Y = 28;
    private static final int ARROW_W = 5;
    private static final int ARROW_H = 7;
    private static final int LEFT_ARROW_X = 16;        // back arrow at the header's left
    private static final int ARROW_HIT_PAD = 3;

    // Overview grid (LIST mode): 2 columns of skill cells, scrollable when skills overflow.
    private static final int FIRST_ROW_Y = 38;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_CONTENT_H = 17;
    private static final int GRID_TOP_LINE_Y = FIRST_ROW_Y - 2;                  // fixed top wrap
    private static final int GRID_BOTTOM_LINE_Y = 158;                           // fixed bottom wrap / region floor
    private static final int GRID_VISIBLE_H = GRID_BOTTOM_LINE_Y - FIRST_ROW_Y;  // 120 px = 6 rows
    private static final int CELL_NAME_X = 1;
    private static final int CELL_NAME_TOP = 3;
    private static final int CELL_BAR_TOP = 13;
    private static final int CELL_BAR_HEIGHT = 2;
    private static final float CELL_TEXT_SCALE = 0.75f;
    private static final int COLUMN_DIVIDER_X = 87;
    private static final int LEFT_CELL_X0 = 13;
    private static final int LEFT_CELL_X1 = 84;
    private static final int RIGHT_CELL_X0 = 91;
    private static final int RIGHT_CELL_X1 = 162;
    private static final int CELL_HIGHLIGHT = 0x33FFE082;   // warm hover wash behind a cell

    private static final int DRAG_THRESHOLD = 4;           // px of movement before a press becomes a pan (not a click)
    private static final long RESPEC_CONFIRM_MS = 4000L;   // window for the second click that confirms a reset
    private static final long RESPEC_CONFIRM_GRACE_MS = 300L;   // min dwell before that second click can commit (defeats double-clicks)

    // Panel chrome. FRAME_X_LEFT is the side inset; FRAME_X_RIGHT bounds the LIST (176-wide) grid. The TREE
    // pop-out is a larger panel sized to its tree, so its frame/canvas/footer are computed from panelW/panelH.
    private static final int FRAME_X_LEFT = 9;          // frame inset from the panel's left/right edge
    private static final int FRAME_X_RIGHT = 167;       // LIST grid right edge (the grid panel is always 176 wide)
    private static final int CYCLER_LINE_Y = 39;        // header divider, from panel top
    private static final int CANVAS_INSET = 10;         // canvas clips 1px inside the side frame
    private static final int HEADER_H = 40;             // panel top → canvas top
    private static final int FOOTER_H = 24;             // canvas bottom → panel bottom
    private static final int FOOTER_TEXT_OFF = 21;      // level/points/reset baseline, from panel bottom
    private static final int XP_BAR_OFF = 10;           // xp bar top, from panel bottom
    private static final int FOOTER_SIDE_PAD = 13;      // level/xp inset from the panel side
    private static final int XP_BAR_H = 3;

    // TREE pop-out: a FIXED page sized to render the full clearing sprite (see TREE_PAGE_* below), trimmed
    // only if it would exceed the screen (which then engages scroll).
    private static final int TREE_PANEL_MARGIN = 24;

    private static final int COLOR_TITLE = 0xFF3F3F3F;
    private static final int COLOR_SEPARATOR = 0xFF8B7355;
    private static final int COLOR_VALUE = 0xFF8B5A2B;
    private static final int COLOR_POINTS = 0xFF2E7D32;
    private static final int COLOR_MUTED = 0xFF7A7A7A;
    private static final int COLOR_ARROW = 0xFF5A4632;
    private static final int COLOR_ARROW_HOVER = 0xFFB8860B;
    private static final int COLOR_RESPEC = 0xFF8B5A2B;
    private static final int COLOR_RESPEC_ARMED = 0xFFC0392B;

    private static final int NODE_SHADOW = 0x40000000;
    private static final int SCROLLBAR_TRACK = 0x33000000;
    private static final int SCROLLBAR_THUMB = 0xAA8B7355;

    private static final int EDGE_SHADOW = 0x55000000;
    private static final int EDGE_LOCKED = 0xFF4A4540;
    private static final int EDGE_REACHABLE = 0xFF909090;   // grayscale path to an AVAILABLE node, slightly lightened
    private static final int EDGE_ACTIVE = 0xFFD9A93E;

    // Animation (all client-only, driven off Util.getMillis()).
    private static final float HOVER_SCALE = 1.12f;       // hovered node grows this much (applied instantly, no tween)
    private static final float POP_SCALE = 1.25f;         // a just-bought/upgraded node enlarges to this (instant, held briefly)
    private static final long POP_HOLD_MS = 220L;         // ...for this long, then snaps back (no tween)
    private static final long BUY_RESEND_MS = 1000L;      // after this, a same-node buy may re-send even if rank hasn't synced
    private static final int TREE_SCROLL_STEP = 20;       // px the tree pans per mouse-wheel notch
    private static final long SLOT_CYCLE_MS = 6000L;      // a slot-cycle session stays armed this long between inputs

    private static final int PERK_ICON = 22;   // per-perk node icon: 22×22 centered in the 26px node (2px inside the frame)
    private static final int PIP_SIZE = 3;
    private static final int PIP_GAP = 1;
    private static final int PIP_Y_OFF = SkillTreeLayout.NODE - PIP_SIZE - 2;   // inside the bottom border
    private static final int PIP_ON = 0xFFFFE082;
    private static final int PIP_OFF = 0xFF2A2A2A;
    private static final int BADGE_COLOR = 0xFFFFD24C;   // bound action-bar slot number on an ability node

    private static final int CLEARING_W = 384;           // clearing.png backdrop dimensions (drawn 1:1)
    private static final int CLEARING_H = 128;
    private static final int TREE_PAGE_W = CLEARING_W + 2 * CANVAS_INSET;       // 404: full-sprite page width
    private static final int TREE_PAGE_H = CLEARING_H + HEADER_H + FOOTER_H;    // 192: fixed page height (~3 rows)
    private static final int BACK_TAIL_LEN = 4;          // shaft length so the back arrow reads "←", not "<"

    // Grid-cell "perk available" flash (LIST mode): a pulsing green outline.
    private static final int FLASH_RGB = 0x33CC33;
    private static final long FLASH_PERIOD_MS = 900L;
    private static final int FLASH_ALPHA_MIN = 0x30;
    private static final int FLASH_ALPHA_MAX = 0xFF;

    private static final Identifier XP_BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier XP_BAR_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    private int leftPos;
    private int topPos;
    private int panelW;   // current panel size: the 176×166 grid in LIST, a larger tree-fitted panel in TREE
    private int panelH;

    /** LIST = the overview grid (landing); TREE = one skill's perk tree (drilled into from the grid). */
    private enum Mode { LIST, TREE }

    private Mode mode = Mode.LIST;
    private int gridScroll;   // LIST-mode vertical scroll (px), engaged only when skills overflow the grid

    /** Skills with a non-empty tree, in curated order then any addon extras. Rebuilt on init. */
    private final List<SkillDefinition> skills = new ArrayList<>();
    private int selectedIndex;

    /** Geometry of the selected skill (player-independent); recomputed only when the selection changes. */
    private TreeGeometry geometry;
    private CanvasViewport viewport;

    // Canvas press tracking: a left-press inside the canvas is a click (→ spend) until it moves past
    // DRAG_THRESHOLD, at which point it becomes a pan. This disambiguates buy-clicks from drags.
    private boolean pressing;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private TreeGeometry.NodeBox pressedNode;

    /** {@link Util#getMillis()} (monotonic client clock) value until which a per-skill reset is "armed". */
    private long respecArmedUntil;

    // The hovered node (NodeView is rebuilt each frame, so it can't hold this); cleared on skill switch.
    private String hoveredPerkId;
    private final Map<String, Integer> knownRanks = new HashMap<>();   // last-seen rank, to detect a just-bought node
    private final Map<String, Long> popStartMs = new HashMap<>();      // a just-bought node enlarges until this + POP_HOLD_MS

    // Spend round-trip guard: don't re-fire a buy on the same node until its rank actually syncs up,
    // so drumming clicks can't overshoot past the rank the player meant to stop at. Released after
    // BUY_RESEND_MS so a (near-impossible) lost/rejected buy can't dead-end the node.
    private String pendingBuyPerkId;
    private int pendingBuyRank;
    private long pendingBuySentMs;

    // The hovered node whose tooltip is drawn last (on a bumped stratum) so it sits above all chrome.
    private LaidOutTree.NodeView pendingTooltipNode;

    // Slot-cycle session (right-click an active to step its action-bar slot). Empty slots bind immediately;
    // a slot held by another ability arms an Enter-to-replace. All client-only, cleared on skill switch.
    private String slotCycleAbility;   // ability id being cycled, or null
    private int slotCycleCursor;       // 0..COUNT-1 = a slot, -1 = the unbound stop
    private int slotCycleCommitted;    // where the cycled ability is actually bound (-1 = unbound)
    private long slotCycleUntil;       // session auto-expiry

    public SkillsScreen() {
        super(Component.translatable("chronicles_leveling.screen.skills.title"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildVisibleSkills();
        clampGridScroll();
        if (selectedIndex >= skills.size()) {
            selectedIndex = Math.max(0, skills.size() - 1);
        }
        layoutForMode();   // sizes the panel for the current mode, (re)builds the tree viewport, places the tab bar
    }

    /**
     * Positions the panel + tab bar for the current mode and rebuilds the tree viewport. LIST is the fixed
     * 176×166 grid with the tab bar above it; TREE is a larger panel sized to fit the selected tree (capped
     * to the screen), shown as a focused pop-out with no tab bar (the back arrow returns to the grid).
     */
    private void layoutForMode() {
        if (mode == Mode.TREE && !skills.isEmpty()) {
            layoutTree();
        } else {
            panelW = IMAGE_WIDTH;
            panelH = IMAGE_HEIGHT;
            leftPos = (this.width - panelW) / 2;
            topPos = (this.height - panelH) / 2;
        }
        clearWidgets();
        if (mode == Mode.LIST || skills.isEmpty()) {
            addRenderableWidget(new ChroniclesTabBar(leftPos, topPos));
        }
    }

    /**
     * Sizes the TREE panel to a FIXED page that renders the full clearing sprite, trimmed only if it would
     * exceed the screen (which then engages scroll). Height is fixed, so trees taller than ~3 rows scroll
     * over the page rather than growing it. Rebuilds the viewport for the resulting canvas.
     */
    private void layoutTree() {
        if (geometry == null) {
            geometry = SkillTreeLayout.geometry(skills.get(selectedIndex), LayeredLayout.INSTANCE);
        }
        panelW = Math.min(TREE_PAGE_W, this.width - 2 * TREE_PANEL_MARGIN);
        panelH = Math.min(TREE_PAGE_H, this.height - 2 * TREE_PANEL_MARGIN);
        leftPos = (this.width - panelW) / 2;
        topPos = (this.height - panelH) / 2;
        viewport = new CanvasViewport(canvasX(), canvasY(), canvasW(), canvasH(), geometry.extent());
    }

    // Panel-relative geometry (TREE chrome scales with panelW/panelH; LIST keeps the 176×166 values).
    private int frameRightX() {
        return leftPos + panelW - FRAME_X_LEFT;
    }

    private int canvasX() {
        return leftPos + CANVAS_INSET;
    }

    private int canvasY() {
        return topPos + HEADER_H;
    }

    private int canvasW() {
        return panelW - 2 * CANVAS_INSET;
    }

    private int canvasH() {
        return panelH - HEADER_H - FOOTER_H;
    }

    private int footerDividerY() {
        return topPos + panelH - FOOTER_H;
    }

    private int footerTextY() {
        return topPos + panelH - FOOTER_TEXT_OFF;
    }

    private int xpBarY() {
        return topPos + panelH - XP_BAR_OFF;
    }

    private void rebuildVisibleSkills() {
        skills.clear();
        for (String id : Skills.ALL) {
            SkillDefinition def = SkillRegistry.get(id);
            if (def != null && !def.perks().isEmpty()) {
                skills.add(def);
            }
        }
        for (SkillDefinition def : SkillRegistry.all()) {
            if (!Skills.ALL.contains(def.id()) && !def.perks().isEmpty()) {
                skills.add(def);   // addon-registered skills, after the curated set
            }
        }
    }

    /** The generic UI click for a tree-navigation interaction (open/close, reset, slot cycle). */
    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    /** Drills from the overview grid into a skill's tree (TREE mode): lays the tree out + sizes the pop-out panel. */
    private void openTree(int index) {
        this.selectedIndex = index;
        this.geometry = SkillTreeLayout.geometry(skills.get(index), LayeredLayout.INSTANCE);
        resetTreeState();
        this.mode = Mode.TREE;
        layoutForMode();   // sizes the panel to the tree + builds the viewport
    }

    /** Returns from a tree to the overview grid (LIST mode). */
    private void backToList() {
        playClick();
        this.mode = Mode.LIST;
        layoutForMode();
    }

    /** Resets per-tree transient interaction state (pan, reset-confirm, hover, buy guard). */
    private void resetTreeState() {
        this.pressing = false;
        this.dragging = false;
        this.pressedNode = null;
        this.respecArmedUntil = 0;
        this.hoveredPerkId = null;
        this.knownRanks.clear();
        this.popStartMs.clear();
        this.pendingBuyPerkId = null;
        this.slotCycleAbility = null;
    }

    // --- render ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (mode == Mode.TREE && !skills.isEmpty()) {
            // Pop-out: the fixed 404×192 panel sprite, center-cropped when the screen trims the page (clearing.png draws over its slot in renderCanvas).
            int u = (TREE_PAGE_W - panelW) / 2;
            int v = (TREE_PAGE_H - panelH) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, ChroniclesTextures.SKILL_TREE_PANEL,
                    leftPos, topPos, u, v, panelW, panelH, TREE_PAGE_W, TREE_PAGE_H);
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, ChroniclesTextures.PARCHMENT,
                leftPos + PARCHMENT_OFFSET_X, topPos + PARCHMENT_OFFSET_Y, 0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ChroniclesTextures.GUI,
                leftPos, topPos, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (skills.isEmpty()) {
            renderTitle(graphics, getTitle());
            renderEmpty(graphics);
            return;
        }
        if (mode == Mode.LIST) {
            renderTitle(graphics, getTitle());
            renderGrid(graphics, mouseX, mouseY);
            return;
        }

        SkillDefinition def = skills.get(selectedIndex);
        PlayerSkillData.SkillEntry entry = currentEntry(def.id());

        renderTitle(graphics, def.display());   // skill name is the tree page's title
        renderTreeHeader(graphics, def, mouseX, mouseY);
        renderFrame(graphics);
        renderCanvas(graphics, entry, mouseX, mouseY);
        renderFooter(graphics, def.id(), entry, mouseX, mouseY);
        renderNodeTooltip(graphics, def, entry, mouseX, mouseY);
    }

    /** The local player's live (synced) skill data, or the default when there's no player. */
    private PlayerSkillData currentData() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? PlayerSkillData.DEFAULT : PlayerSkillManager.get(player);
    }

    private PlayerSkillData.SkillEntry currentEntry(String skillId) {
        return currentData().get(skillId);
    }

    private void renderTitle(GuiGraphicsExtractor graphics, Component title) {
        int textW = font.width(title);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos + panelW / 2f, topPos + TITLE_Y);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.text(font, title, -textW / 2, 0, COLOR_TITLE, false);
        graphics.pose().popMatrix();
        drawHorizontalLine(graphics, leftPos + FRAME_X_LEFT, frameRightX(), topPos + HEADER_LINE_Y);
    }

    private void renderEmpty(GuiGraphicsExtractor graphics) {
        renderFrame(graphics);   // keep the canvas box so the empty state reads as deliberate, not half-drawn
        Component msg = Component.translatable("chronicles_leveling.skill.tree.empty");
        int x = centeredX(font.width(msg));
        int y = (topPos + CYCLER_LINE_Y + footerDividerY()) / 2 - font.lineHeight / 2;
        graphics.text(font, msg, x, y, COLOR_MUTED, false);
    }

    private void renderTreeHeader(GuiGraphicsExtractor graphics, SkillDefinition def, int mouseX, int mouseY) {
        // Back affordance: "←  Skills" returning to the overview grid.
        int backColor = overBackArrow(mouseX, mouseY) ? COLOR_ARROW_HOVER : COLOR_ARROW;
        int arrowX = leftPos + LEFT_ARROW_X;
        int arrowY = topPos + CYCLER_Y;
        drawArrow(graphics, arrowX, arrowY, true, backColor);
        int midY = arrowY + (ARROW_H - 1) / 2;
        graphics.fill(arrowX + ARROW_W, midY, arrowX + ARROW_W + BACK_TAIL_LEN, midY + 1, backColor);   // shaft → "←"
        graphics.text(font, backLabel(), arrowX + ARROW_W + BACK_TAIL_LEN + 3, arrowY, backColor, false);

        // The title shows the skill name; hovering it reveals the skill's description.
        if (def.description().isPresent() && overTitle(mouseX, mouseY, def.display())) {
            List<Component> tip = new ArrayList<>();
            tip.add(def.display());
            tip.add(def.description().get().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    // --- overview grid (LIST mode) ---

    private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x0 = leftPos + FRAME_X_LEFT;
        int x1 = leftPos + FRAME_X_RIGHT;
        // Fixed table frame around the scrollable cell region.
        drawHorizontalLine(graphics, x0, x1, topPos + GRID_TOP_LINE_Y);
        drawHorizontalLine(graphics, x0, x1, topPos + GRID_BOTTOM_LINE_Y);
        drawVerticalLine(graphics, x0, topPos + GRID_TOP_LINE_Y, topPos + GRID_BOTTOM_LINE_Y);
        drawVerticalLine(graphics, x1 - 1, topPos + GRID_TOP_LINE_Y, topPos + GRID_BOTTOM_LINE_Y);
        drawVerticalLine(graphics, leftPos + COLUMN_DIVIDER_X, topPos + GRID_TOP_LINE_Y, topPos + GRID_BOTTOM_LINE_Y);

        int regionTop = topPos + FIRST_ROW_Y - 1;
        int regionBottom = topPos + GRID_BOTTOM_LINE_Y;
        int hoveredIndex = gridCellAt(mouseX, mouseY);
        PlayerSkillData data = currentData();

        graphics.enableScissor(x0 + 1, regionTop, x1 - 1, regionBottom);
        for (int i = 0; i < skills.size(); i++) {
            boolean left = (i % 2) == 0;
            int cellY = topPos + FIRST_ROW_Y + (i / 2) * ROW_HEIGHT - gridScroll;
            if (cellY + ROW_CONTENT_H < regionTop || cellY > regionBottom) {
                continue;   // fully scrolled out of view
            }
            renderSkillCell(graphics, skills.get(i), data, left, cellY, i == hoveredIndex, mouseX, mouseY);
            if (left) {   // one separator under each row, drawn with the row's left cell
                drawHorizontalLine(graphics, x0, x1, cellY + ROW_CONTENT_H + 1);
            }
        }
        graphics.disableScissor();

        drawGridScrollbar(graphics, x1, regionTop, regionBottom);
    }

    private void renderSkillCell(GuiGraphicsExtractor graphics, SkillDefinition def, PlayerSkillData data,
                                 boolean left, int cellY, boolean hovered, int mouseX, int mouseY) {
        int cellX0 = leftPos + (left ? LEFT_CELL_X0 : RIGHT_CELL_X0);
        int cellX1 = leftPos + (left ? LEFT_CELL_X1 : RIGHT_CELL_X1);
        int colX0 = leftPos + (left ? FRAME_X_LEFT + 1 : COLUMN_DIVIDER_X + 1);
        int colX1 = leftPos + (left ? COLUMN_DIVIDER_X : FRAME_X_RIGHT - 1);
        PlayerSkillData.SkillEntry entry = data.get(def.id());

        if (hovered) {
            graphics.fill(colX0, cellY - 1, colX1, cellY + ROW_CONTENT_H, CELL_HIGHLIGHT);
        }

        Component name = def.display();
        int textY = cellY + CELL_NAME_TOP;

        // Name (left-aligned, scaled around its top-left anchor).
        graphics.pose().pushMatrix();
        graphics.pose().translate(cellX0 + CELL_NAME_X, textY);
        graphics.pose().scale(CELL_TEXT_SCALE, CELL_TEXT_SCALE);
        graphics.text(font, name, 0, 0, COLOR_TITLE, false);
        graphics.pose().popMatrix();

        // Level (right-aligned at the cell's right edge).
        String levelText = Integer.toString(entry.level());
        int levelW = font.width(levelText);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cellX1, textY);
        graphics.pose().scale(CELL_TEXT_SCALE, CELL_TEXT_SCALE);
        graphics.text(font, levelText, -levelW, 0, COLOR_VALUE, false);
        graphics.pose().popMatrix();

        int barWidth = cellX1 - cellX0;
        int barY = cellY + CELL_BAR_TOP;
        int xpToNext = SkillCurve.xpToNext(def.id(), entry.level());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND, cellX0, barY, barWidth, CELL_BAR_HEIGHT);
        if (xpToNext > 0 && entry.xp() > 0) {
            int progressW = (int) Math.min(barWidth, ((long) barWidth * entry.xp()) / xpToNext);
            if (progressW > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS,
                        barWidth, CELL_BAR_HEIGHT, 0, 0, cellX0, barY, progressW, CELL_BAR_HEIGHT);
            }
        }

        // Pulsing green outline when this skill has a perk affordable right now.
        if (hasPurchasablePerk(def, entry)) {
            drawCellFlash(graphics, colX0, cellY - 1, colX1 - colX0, ROW_CONTENT_H + 1);
        }

        // Over the bar → XP progress; anywhere else on the cell → name + description.
        if (ProgressTooltip.isHovered(mouseX, mouseY, cellX0, barY, barWidth, CELL_BAR_HEIGHT)) {
            graphics.setTooltipForNextFrame(font, ProgressTooltip.build(entry.xp(), xpToNext), mouseX, mouseY);
        } else if (hovered) {
            List<Component> tip = new ArrayList<>();
            tip.add(name);
            def.description().ifPresent(d -> tip.add(d.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    /** A right-edge scroll thumb, only when the grid overflows its visible region. */
    private void drawGridScrollbar(GuiGraphicsExtractor graphics, int rightEdge, int regionTop, int regionBottom) {
        int range = maxGridScroll();
        if (range <= 0) {
            return;
        }
        int trackH = regionBottom - regionTop;
        int contentH = gridRows() * ROW_HEIGHT;
        int barX = rightEdge - 3;
        int thumb = Math.max(8, (int) ((long) trackH * GRID_VISIBLE_H / contentH));
        int travel = trackH - thumb;
        int thumbY = regionTop + (int) Math.round((double) travel * gridScroll / range);
        graphics.fill(barX, regionTop, barX + 2, regionBottom, SCROLLBAR_TRACK);
        graphics.fill(barX, thumbY, barX + 2, thumbY + thumb, SCROLLBAR_THUMB);
    }

    /** The grid cell index under the cursor, or {@code -1}; accounts for scroll + clips to the visible region. */
    private int gridCellAt(double mouseX, double mouseY) {
        int regionTop = topPos + FIRST_ROW_Y - 1;
        int regionBottom = topPos + GRID_BOTTOM_LINE_Y;
        if (mouseY < regionTop || mouseY >= regionBottom) {
            return -1;
        }
        int col;
        if (mouseX >= leftPos + FRAME_X_LEFT + 1 && mouseX < leftPos + COLUMN_DIVIDER_X) {
            col = 0;
        } else if (mouseX >= leftPos + COLUMN_DIVIDER_X + 1 && mouseX < leftPos + FRAME_X_RIGHT - 1) {
            col = 1;
        } else {
            return -1;   // on a frame line / divider
        }
        double contentY = mouseY - (topPos + FIRST_ROW_Y) + gridScroll;
        if (contentY < 0 || contentY % ROW_HEIGHT > ROW_CONTENT_H) {
            return -1;   // above the first row, or in a row's separator gap
        }
        int index = (int) (contentY / ROW_HEIGHT) * 2 + col;
        return index < skills.size() ? index : -1;
    }

    private int gridRows() {
        return (skills.size() + 1) / 2;
    }

    private int maxGridScroll() {
        return Math.max(0, gridRows() * ROW_HEIGHT - GRID_VISIBLE_H);
    }

    private void clampGridScroll() {
        gridScroll = Math.max(0, Math.min(gridScroll, maxGridScroll()));
    }

    /** A pulsing green outline drawn on a grid cell that has at least one perk the player can buy now. */
    private void drawCellFlash(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        float t = 0.5f + 0.5f * (float) Math.sin(
                (Util.getMillis() % FLASH_PERIOD_MS) / (float) FLASH_PERIOD_MS * 2f * (float) Math.PI);
        int alpha = (int) Easing.lerp(FLASH_ALPHA_MIN, FLASH_ALPHA_MAX, t);
        drawThickOutline(graphics, x, y, w, h, (alpha << 24) | FLASH_RGB);
    }

    /** Whether the player can buy at least one perk in this skill right now (prereqs met + affordable). */
    private static boolean hasPurchasablePerk(SkillDefinition def, PlayerSkillData.SkillEntry entry) {
        int totalCost = def.totalCost();
        for (SkillPerk perk : def.perks()) {
            if (perkBuyable(perk, entry, totalCost)) {
                return true;
            }
        }
        return false;
    }

    /** Total SP to complete the currently-selected tree; the cap on accrued/available points. */
    private int selectedTotalCost() {
        return skills.get(selectedIndex).totalCost();
    }

    /** Whether this perk's next rank can be bought now: not maxed, prereqs at rank >= 1, enough points. */
    private static boolean perkBuyable(SkillPerk perk, PlayerSkillData.SkillEntry entry, int totalCost) {
        int rank = entry.rankOf(perk.id());
        if (rank >= perk.maxRank()) {
            return false;
        }
        if (!perk.prerequisitesMet(pre -> entry.rankOf(pre) >= 1)) {
            return false;
        }
        return entry.availablePoints(totalCost) >= perk.costOfNextRank(rank);
    }

    private void renderFrame(GuiGraphicsExtractor graphics) {
        int x0 = leftPos + FRAME_X_LEFT;
        int x1 = frameRightX();
        int top = topPos + CYCLER_LINE_Y;
        int bottom = footerDividerY();
        drawHorizontalLine(graphics, x0, x1, top);
        drawHorizontalLine(graphics, x0, x1, bottom);
        drawVerticalLine(graphics, x0, top, bottom);
        drawVerticalLine(graphics, x1 - 1, top, bottom);
    }

    private void renderCanvas(GuiGraphicsExtractor graphics,
                              PlayerSkillData.SkillEntry entry, int mouseX, int mouseY) {
        pendingTooltipNode = null;
        LaidOutTree tree = SkillTreeLayout.project(geometry, entry, selectedTotalCost());
        Map<String, LaidOutTree.NodeView> byId = new HashMap<>();
        for (LaidOutTree.NodeView node : tree.nodes()) {
            byId.put(node.box().perk().id(), node);
        }

        TreeGeometry.NodeBox hovered = viewport.nodeAt(geometry.nodes(), mouseX, mouseY);
        updateHover(hovered);
        detectUnlocks(tree);

        // The clearing is FIXED to the page (it does not pan); only the tree layer scrolls over it.
        drawClearingFixed(graphics);

        viewport.runClipped(graphics, () -> {
            for (TreeGeometry.Edge edge : tree.edges()) {
                if (!edgeLit(edge, byId)) {
                    drawEdge(graphics, edge, byId);
                }
            }
            for (TreeGeometry.Edge edge : tree.edges()) {
                if (edgeLit(edge, byId)) {
                    drawEdge(graphics, edge, byId);   // lit paths last, so a darkened edge can't cover them
                }
            }
            for (LaidOutTree.NodeView node : tree.nodes()) {
                drawNode(graphics, node);
            }
        });
        viewport.drawScrollbars(graphics, SCROLLBAR_TRACK, SCROLLBAR_THUMB);

        // Defer the tooltip to end-of-frame (renderNodeTooltip) so the custom painter lands on a bumped
        // stratum above the footer. The hit-test matches the viewport's clipping, so the hover bound can't
        // drift; it consumes the already-projected NodeView, so the gem and its tooltip can never disagree.
        if (hovered != null) {
            pendingTooltipNode = byId.get(hovered.perk().id());
        }
    }

    /**
     * The clearing backdrop, FIXED to the canvas (it does not pan); the tree's nodes/edges scroll over it.
     * Drawn 1:1 (same pixel density as everything else); when the panel is screen-trimmed narrower/shorter
     * than the sprite, the sprite's center is shown.
     */
    private void drawClearingFixed(GuiGraphicsExtractor graphics) {
        int cw = Math.min(canvasW(), CLEARING_W);
        int ch = Math.min(canvasH(), CLEARING_H);
        int u = (CLEARING_W - cw) / 2;
        int v = (CLEARING_H - ch) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ChroniclesTextures.CLEARING,
                canvasX(), canvasY(), u, v, cw, ch, CLEARING_W, CLEARING_H);
    }

    /** Tracks which node the cursor is over. */
    private void updateHover(TreeGeometry.NodeBox hovered) {
        hoveredPerkId = hovered == null ? null : hovered.perk().id();
    }

    /** Flags a node that just gained a rank (a buy landed) so it briefly enlarges as feedback. */
    private void detectUnlocks(LaidOutTree tree) {
        long now = Util.getMillis();
        boolean bought = false;
        for (LaidOutTree.NodeView node : tree.nodes()) {
            Integer previous = knownRanks.put(node.box().perk().id(), node.rank());
            if (previous != null && node.rank() > previous) {
                popStartMs.put(node.box().perk().id(), now);
                bought = true;
            }
        }
        // A confirmed rank buy pops a bubble
        // The buy that maxes the whole tree plays the level-up jingle instead.
        if (bought) {
            boolean allMaxed = tree.nodes().stream().allMatch(n -> n.state() == LaidOutTree.NodeState.MAXED);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    allMaxed ? ModSounds.LEVEL_UP.value() : SoundEvents.BUBBLE_POP, 1.0f, 1.0f));
        }
    }

    private void renderFooter(GuiGraphicsExtractor graphics, String skillId,
                              PlayerSkillData.SkillEntry entry, int mouseX, int mouseY) {
        Component level = Component.translatable("chronicles_leveling.skill.tree.level", entry.level());
        graphics.text(font, level, leftPos + FOOTER_SIDE_PAD, footerTextY(), COLOR_VALUE, false);

        int available = entry.availablePoints(selectedTotalCost());
        Component points = Component.translatable("chronicles_leveling.skill.tree.points", available);
        int pointsW = font.width(points);
        int pointsX = leftPos + panelW - FOOTER_SIDE_PAD - pointsW;
        graphics.text(font, points, pointsX, footerTextY(), available > 0 ? COLOR_POINTS : COLOR_MUTED, false);
        if (hovers(mouseX, mouseY, pointsX, footerTextY(), pointsW, font.lineHeight)) {
            graphics.setTooltipForNextFrame(font,
                    Component.translatable("chronicles_leveling.skill.tree.points.tooltip", available), mouseX, mouseY);
        }

        renderRespec(graphics, entry, mouseX, mouseY);
        renderXpBar(graphics, skillId, entry, mouseX, mouseY);
    }

    /** Centered reset control between the level + points readouts; greyed when nothing is spent. */
    private void renderRespec(GuiGraphicsExtractor graphics, PlayerSkillData.SkillEntry entry, int mouseX, int mouseY) {
        boolean canRespec = entry.spentPoints() > 0;
        Component label = respecLabel(entry);
        int w = font.width(label);
        int x = centeredX(w);
        boolean hover = canRespec && hovers(mouseX, mouseY, x, footerTextY(), w, font.lineHeight);
        int color = !canRespec ? COLOR_MUTED
                : respecArmed() ? COLOR_RESPEC_ARMED
                : hover ? COLOR_ARROW_HOVER : COLOR_RESPEC;
        graphics.text(font, label, x, footerTextY(), color, false);
        if (hover) {
            Component tip = respecArmed()
                    ? Component.translatable("chronicles_leveling.skill.tree.respec.confirm.tooltip")
                    : Component.translatable("chronicles_leveling.skill.tree.respec.tooltip", entry.spentPoints());
            graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    private void renderXpBar(GuiGraphicsExtractor graphics, String skillId,
                             PlayerSkillData.SkillEntry entry, int mouseX, int mouseY) {
        int barX = leftPos + FOOTER_SIDE_PAD;
        int barY = xpBarY();
        int barW = panelW - 2 * FOOTER_SIDE_PAD;
        int xpToNext = SkillCurve.xpToNext(skillId, entry.level());

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND, barX, barY, barW, XP_BAR_H);
        if (xpToNext > 0 && entry.xp() > 0) {
            int progressW = (int) Math.min(barW, ((long) barW * entry.xp()) / xpToNext);
            if (progressW > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS,
                        barW, XP_BAR_H, 0, 0, barX, barY, progressW, XP_BAR_H);
            }
        }
        if (ProgressTooltip.isHovered(mouseX, mouseY, barX, barY, barW, XP_BAR_H)) {
            graphics.setTooltipForNextFrame(font, ProgressTooltip.build(entry.xp(), xpToNext), mouseX, mouseY);
        }
    }

    // --- tree drawing (content space) ---

    private void drawEdge(GuiGraphicsExtractor graphics, TreeGeometry.Edge edge,
                          Map<String, LaidOutTree.NodeView> byId) {
        int color = edgeColor(byId.get(edge.parent().perk().id()).state(),
                byId.get(edge.child().perk().id()).state());
        for (TreeGeometry.Line seg : edge.segments()) {
            fillSegment(graphics, seg, 1, 1, EDGE_SHADOW);   // drop shadow for depth
            fillSegment(graphics, seg, 0, 0, color);
        }
    }

    /** Edge tint follows the CHILD's node state so a lit connector never points at a greyed-out node. */
    private static int edgeColor(LaidOutTree.NodeState parent, LaidOutTree.NodeState child) {
        boolean parentOwned = parent == LaidOutTree.NodeState.UNLOCKED || parent == LaidOutTree.NodeState.MAXED;
        boolean childOwned = child == LaidOutTree.NodeState.UNLOCKED || child == LaidOutTree.NodeState.MAXED;
        if (parentOwned && childOwned) {
            return EDGE_ACTIVE;
        }
        if (parentOwned && child == LaidOutTree.NodeState.AVAILABLE) {
            return EDGE_REACHABLE;
        }
        return EDGE_LOCKED;
    }

    /** A "lit" edge connects two owned nodes (EDGE_ACTIVE); drawn last so darkened edges can't cover it. */
    private boolean edgeLit(TreeGeometry.Edge edge, Map<String, LaidOutTree.NodeView> byId) {
        return edgeColor(byId.get(edge.parent().perk().id()).state(),
                byId.get(edge.child().perk().id()).state()) == EDGE_ACTIVE;
    }

    /** Fills a 1px-thick axis-aligned segment (offset by ox/oy) as a rectangle. */
    private static void fillSegment(GuiGraphicsExtractor graphics, TreeGeometry.Line seg, int ox, int oy, int color) {
        int x0 = Math.min(seg.x0(), seg.x1()) + ox;
        int y0 = Math.min(seg.y0(), seg.y1()) + oy;
        int x1 = Math.max(seg.x0(), seg.x1()) + 1 + ox;
        int y1 = Math.max(seg.y0(), seg.y1()) + 1 + oy;
        graphics.fill(x0, y0, x1, y1, color);
    }

    private void drawNode(GuiGraphicsExtractor graphics, LaidOutTree.NodeView node) {
        int x = node.box().x();
        int y = node.box().y();
        int n = SkillTreeLayout.NODE;
        SkillPerk perk = node.box().perk();
        LaidOutTree.NodeState state = node.state();
        boolean owned = state == LaidOutTree.NodeState.UNLOCKED || state == LaidOutTree.NodeState.MAXED;
        boolean locked = state == LaidOutTree.NodeState.LOCKED;

        float scale = nodeScale(perk.id());
        boolean scaled = scale != 1f;
        if (scaled) {   // scale about the node's center for hover-grow + unlock-pop
            float cx = x + n / 2f;
            float cy = y + n / 2f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-cx, -cy);
        }

        graphics.fill(x + 1, y + 1, x + n + 1, y + n + 1, NODE_SHADOW);
        // Perk art (desaturated when the node is locked), centered inside the frame.
        Identifier icon = locked
                ? ChroniclesTextures.perkLocked(perk.owningSkill(), perk.id())
                : ChroniclesTextures.perk(perk.owningSkill(), perk.id());
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                x + (n - PERK_ICON) / 2, y + (n - PERK_ICON) / 2, 0f, 0f, PERK_ICON, PERK_ICON, PERK_ICON, PERK_ICON);
        // State frame on top: selected (owned) vs unselected (available/locked).
        Identifier frame = owned ? ChroniclesTextures.NODE_FRAME_SELECTED : ChroniclesTextures.NODE_FRAME_UNSELECTED;
        graphics.blit(RenderPipelines.GUI_TEXTURED, frame, x, y, 0f, 0f, n, n, n, n);

        if (perk.maxRank() > 1) {
            drawPips(graphics, x, y, node.rank(), perk.maxRank());
        }

        // Unlocked ability node: badge its bound action-bar slot (click cycles it).
        Identifier ability = abilityOf(perk);
        if (ability != null && node.rank() >= 1) {
            drawSlotBadge(graphics, x, y, currentData().slotOf(ability.toString()));
        }

        if (scaled) {
            graphics.pose().popMatrix();
        }
    }

    /** Hovered or just-bought nodes grow instantly (no tween); a buy-pop holds briefly, then snaps back. */
    private float nodeScale(String perkId) {
        float scale = perkId.equals(hoveredPerkId) ? HOVER_SCALE : 1f;
        Long popStart = popStartMs.get(perkId);
        if (popStart != null) {
            if (Util.getMillis() - popStart >= POP_HOLD_MS) {
                popStartMs.remove(perkId);   // pop finished
            } else {
                scale = Math.max(scale, POP_SCALE);
            }
        }
        return scale;
    }


    private static void drawPips(GuiGraphicsExtractor graphics, int nodeX, int nodeY, int rank, int maxRank) {
        int totalW = maxRank * PIP_SIZE + (maxRank - 1) * PIP_GAP;
        int px = nodeX + Math.max(0, SkillTreeLayout.NODE - totalW) / 2;   // never spill past the node's left edge
        int py = nodeY + PIP_Y_OFF;
        for (int i = 0; i < maxRank; i++) {
            graphics.fill(px, py, px + PIP_SIZE, py + PIP_SIZE, i < rank ? PIP_ON : PIP_OFF);
            px += PIP_SIZE + PIP_GAP;
        }
    }

    /** Top-right badge on an unlocked ability node: its bound slot number, or a faint dot when unbound. */
    private void drawSlotBadge(GuiGraphicsExtractor graphics, int nodeX, int nodeY, int slot) {
        int n = SkillTreeLayout.NODE;
        if (slot < 0) {
            graphics.fill(nodeX + n - 4, nodeY + 2, nodeX + n - 2, nodeY + 4, 0x80FFFFFF);   // unbound: "click to bind"
            return;
        }
        Component label = Component.literal(Integer.toString(slot + 1));
        graphics.text(font, label, nodeX + n - 1 - font.width(label), nodeY + 1, BADGE_COLOR, true);
    }

    /** The ability a perk unlocks (its first {@link AbilityUnlock} effect), or {@code null} if it's not an ability node. */
    private static Identifier abilityOf(SkillPerk perk) {
        for (PerkEffect effect : perk.effectsAtRank(1)) {
            if (effect instanceof AbilityUnlock unlock) {
                return unlock.abilityId();
            }
        }
        return null;
    }

    /** 2px outline (the 1px variant reads translucent against parchment), matching the Levels screen. */
    private static void drawThickOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.outline(x, y, w, h, color);
        if (w > 2 && h > 2) {
            graphics.outline(x + 1, y + 1, w - 2, h - 2, color);
        }
    }

    /** Filled triangle in an {@code ARROW_W}×{@code ARROW_H} box: tip at the left- or right-middle. */
    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y, boolean pointLeft, int color) {
        int mid = Math.max(1, (ARROW_H - 1) / 2);   // guard the divide; ARROW_H is meant to be odd and >= 3
        for (int row = 0; row < ARROW_H; row++) {
            int inset = (Math.abs(row - mid) * ARROW_W) / mid;   // 0 at the middle row, ARROW_W at the ends
            if (pointLeft) {
                graphics.fill(x + inset, y + row, x + ARROW_W, y + row + 1, color);
            } else {
                graphics.fill(x, y + row, x + ARROW_W - inset, y + row + 1, color);
            }
        }
    }

    private void drawHorizontalLine(GuiGraphicsExtractor graphics, int x0, int x1, int y) {
        graphics.fill(x0, y, x1, y + 1, COLOR_SEPARATOR);
    }

    private void drawVerticalLine(GuiGraphicsExtractor graphics, int x, int y0, int y1) {
        graphics.fill(x, y0, x + 1, y1 + 1, COLOR_SEPARATOR);
    }

    // --- node tooltip ---

    private void renderNodeTooltip(GuiGraphicsExtractor graphics, SkillDefinition def,
                                   PlayerSkillData.SkillEntry entry, int mouseX, int mouseY) {
        LaidOutTree.NodeView node = pendingTooltipNode;
        if (node == null) {
            return;
        }
        SkillPerk perk = node.box().perk();
        Component desc = PerkTooltipRenderer.buildDescComponent(
                perk, node.rank(), entry.level(), perkBuyable(perk, entry, def.totalCost()));
        PerkTooltipRenderer.render(graphics, font, buildNodeTooltipMeta(def, node, entry),
                desc, mouseX, mouseY, this.width, this.height);
    }

    /** Title + rank + action + ability-slot lines. The description is built (with live values) separately. */
    private List<Component> buildNodeTooltipMeta(SkillDefinition def, LaidOutTree.NodeView node,
                                                 PlayerSkillData.SkillEntry entry) {
        SkillPerk perk = node.box().perk();
        int rank = node.rank();
        LaidOutTree.NodeState state = node.state();

        List<Component> lines = new ArrayList<>();
        lines.add(perkName(perk).withStyle(titleStyle(state)));
        int beforeStats = lines.size();
        appendAbilityStats(lines, perk);
        if (lines.size() > beforeStats) {
            lines.add(beforeStats, PerkTooltipRenderer.SECTION_SEPARATOR);   // a rule above the ability stats
        }
        if (perk.maxRank() > 1) {
            lines.add(Component.translatable("chronicles_leveling.skill.tree.rank", rank, perk.maxRank())
                    .withStyle(ChatFormatting.GRAY));
        }
        appendActionLines(lines, def, perk, rank, state, entry);

        // Unlocked ability node: its binding, or the armed overwrite prompt while it's being slot-cycled.
        Identifier ability = abilityOf(perk);
        if (ability != null && rank >= 1) {
            if (ability.toString().equals(slotCycleAbility) && slotCycleActive()) {
                lines.add(slotCycleArmed()
                        ? Component.translatable("chronicles_leveling.skill.tree.slot.replace", slotCycleCursor + 1,
                                abilityDisplayName(currentData().slotAbility(slotCycleCursor))).withStyle(ChatFormatting.YELLOW)
                        : slotLine(slotCycleCommitted));   // local state, so it doesn't lag the server round-trip
            } else {
                lines.add(slotLine(currentData().slotOf(ability.toString())));
            }
        }
        return lines;
    }

    /**
     * Active-ability metadata (duration / cooldown / resource cost) read live from the ability, so the
     * tooltip never hardcodes these. Shown on any ability node, owned or not.
     */
    private void appendAbilityStats(List<Component> lines, SkillPerk perk) {
        Identifier abilityId = abilityOf(perk);
        if (abilityId == null) {
            return;
        }
        SkillAbility ability = SkillRegistry.ability(abilityId);
        if (ability == null) {
            return;
        }
        if (ability.durationTicks() > 0
                && !PerkTooltipRenderer.inlinesDuration(perk.owningSkill(), perk.id())) {
            lines.add(Component.translatable("chronicles_leveling.skill.tree.ability.duration",
                    seconds(ability.durationTicks())).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("chronicles_leveling.skill.tree.ability.cooldown",
                seconds(ability.baseCooldownTicks())).withStyle(ChatFormatting.GRAY));
        AbilityCost cost = ability.cost();
        if (cost.stamina() > 0f) {
            lines.add(Component.translatable("chronicles_leveling.skill.tree.ability.stamina",
                    number(cost.stamina())).withStyle(ChatFormatting.GRAY));
        } else if (cost.mana() > 0f) {
            lines.add(Component.translatable("chronicles_leveling.skill.tree.ability.mana",
                    number(cost.mana())).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String seconds(int ticks) {
        return number(ticks / 20.0) + "s";
    }

    /** Whole number when near-integer, else one decimal (matches the perk tooltip's value format). */
    private static String number(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.05 ? Long.toString(rounded) : String.format("%.1f", value);
    }

    /**
     * The ability-node binding line: a click-to-assign prompt when unbound; a "bind a key in Controls"
     * prompt when assigned to a slot whose cast key is still unbound; else the slot number + its key.
     */
    private Component slotLine(int slot) {
        if (slot < 0 || slot >= ChroniclesKeybinds.ABILITY_SLOTS.length) {
            return Component.translatable("chronicles_leveling.skill.tree.slot.unbound").withStyle(ChatFormatting.AQUA);
        }
        KeyMapping key = ChroniclesKeybinds.ABILITY_SLOTS[slot];
        MutableComponent line = key.isUnbound()
                ? Component.translatable("chronicles_leveling.skill.tree.slot.needs_key", slot + 1)
                : Component.translatable("chronicles_leveling.skill.tree.slot.bound", slot + 1, key.getTranslatedKeyMessage());
        return line.withStyle(ChatFormatting.AQUA);
    }

    /**
     * Appends the action area: a cost/maxed line, or for a node locked on prerequisites the
     * "requires at least N more of:" header plus one line per still-unowned prerequisite (the
     * blocking nodes the player could pursue). Handles "any K of N" via {@link SkillPerk#prerequisitesStillNeeded}.
     */
    private void appendActionLines(List<Component> lines, SkillDefinition def, SkillPerk perk, int rank,
                                   LaidOutTree.NodeState state, PlayerSkillData.SkillEntry entry) {
        switch (state) {
            case MAXED -> lines.add(Component.translatable("chronicles_leveling.skill.tree.maxed").withStyle(ChatFormatting.GOLD));
            case AVAILABLE, UNLOCKED -> lines.add(costLine(perk, rank, def, entry));
            case LOCKED -> {
                Predicate<String> owned = pre -> entry.rankOf(pre) >= 1;
                if (perk.prerequisitesMet(owned)) {
                    lines.add(costLine(perk, rank, def, entry));   // prerequisites met, just unaffordable: red "Required"
                } else if (perk.requiredPrerequisites() == perk.prerequisites().size()) {
                    // Every prerequisite is required (no choice): one inline "Requires: A, B" line.
                    lines.add(Component.translatable("chronicles_leveling.skill.tree.requires",
                            unmetList(def, perk, owned)).withStyle(ChatFormatting.RED));
                } else {
                    // "Any K of N": how many more are needed, then a line per still-unowned candidate.
                    lines.add(Component.translatable("chronicles_leveling.skill.tree.requires_count",
                            perk.prerequisitesStillNeeded(owned)).withStyle(ChatFormatting.RED));
                    for (String pre : perk.prerequisites()) {
                        if (owned.test(pre)) {
                            continue;
                        }
                        lines.add(prerequisiteName(def, pre).withStyle(ChatFormatting.RED));
                    }
                }
            }
        }
    }

    /** Green "Cost: N SP" when the next rank is affordable; red "Required: N SP" when the player lacks the points. */
    private static Component costLine(SkillPerk perk, int rank, SkillDefinition def, PlayerSkillData.SkillEntry entry) {
        int cost = perk.costOfNextRank(rank);
        if (entry.availablePoints(def.totalCost()) >= cost) {
            return Component.translatable("chronicles_leveling.skill.tree.cost", cost).withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("chronicles_leveling.skill.tree.required", cost).withStyle(ChatFormatting.RED);
    }

    /** Comma-joined names of a perk's still-unowned prerequisites, for the all-required "Requires: A, B" line. */
    private static MutableComponent unmetList(SkillDefinition def, SkillPerk perk, Predicate<String> owned) {
        MutableComponent acc = null;
        for (String pre : perk.prerequisites()) {
            if (owned.test(pre)) {
                continue;
            }
            MutableComponent name = prerequisiteName(def, pre);
            acc = acc == null ? name : acc.append(", ").append(name);
        }
        return acc;
    }

    private static MutableComponent prerequisiteName(SkillDefinition def, String prereqId) {
        SkillPerk prePerk = def.perk(prereqId);
        return prePerk != null ? perkName(prePerk) : Component.literal(humanize(prereqId));
    }

    private static MutableComponent perkName(SkillPerk perk) {
        return Component.translatableWithFallback(
                "chronicles_leveling.perk." + perk.owningSkill() + "." + perk.id(), humanize(perk.id()));
    }

    /** Turns a snake_case perk id into Title Case for the fallback display name. */
    private static String humanize(String id) {
        StringBuilder sb = new StringBuilder(id.length());
        for (String word : id.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return sb.toString();
    }

    private static ChatFormatting titleStyle(LaidOutTree.NodeState state) {
        return switch (state) {
            case LOCKED -> ChatFormatting.GRAY;
            case AVAILABLE -> ChatFormatting.WHITE;
            case UNLOCKED -> ChatFormatting.GREEN;
            case MAXED -> ChatFormatting.GOLD;
        };
    }

    // --- input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;   // tab bar etc.
        }
        // Right-click an unlocked ability node to (re)bind its action-bar slot; left-click is reserved for
        // ranking the node up, so multi-rank ability perks (e.g. Essence Channeller) can still be leveled.
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (mode == Mode.TREE && !skills.isEmpty() && viewport != null) {
                var node = viewport.nodeAt(geometry.nodes(), event.x(), event.y());
                if (node != null) {
                    SkillPerk perk = node.perk();
                    Identifier ability = abilityOf(perk);
                    if (ability != null && currentEntry(perk.owningSkill()).rankOf(perk.id()) >= 1) {
                        cycleSlot(ability);
                        return true;
                    }
                }
            }
            return false;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || skills.isEmpty()) {
            return false;
        }
        double mx = event.x();
        double my = event.y();

        if (mode == Mode.LIST) {
            int cell = gridCellAt(mx, my);
            if (cell >= 0) {
                playClick();
                openTree(cell);
                return true;
            }
            return false;
        }

        // TREE mode.
        SkillDefinition def = skills.get(selectedIndex);
        PlayerSkillData.SkillEntry entry = currentEntry(def.id());

        // Capture whether an armed reset is past its dwell grace BEFORE disarming. Every left-press
        // disarms; only the reset button (below) re-arms, so a stale confirm can't survive a click
        // elsewhere, and a reflexive double-click re-arms within the grace instead of committing.
        boolean confirmReset = respecConfirmable();
        respecArmedUntil = 0;

        if (overBackArrow(mx, my)) {
            backToList();
            return true;
        }
        if (overRespec(mx, my, entry)) {
            if (entry.spentPoints() > 0) {
                playClick();
                if (confirmReset) {
                    NetworkDispatcher.sendRespecSkill(def.id());
                } else {
                    respecArmedUntil = Util.getMillis() + RESPEC_CONFIRM_MS;   // arm; a deliberate later click confirms
                }
            }
            return true;
        }
        if (viewport != null && viewport.contains(mx, my)) {
            pressing = true;
            dragging = false;
            pressX = mx;
            pressY = my;
            pressedNode = viewport.nodeAt(geometry.nodes(), mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode == Mode.LIST && maxGridScroll() > 0) {
            gridScroll = (int) Math.max(0, Math.min(gridScroll - scrollY * ROW_HEIGHT, maxGridScroll()));
            return true;
        }
        if (mode == Mode.TREE && viewport != null && viewport.contains(mouseX, mouseY)) {
            viewport.scrollBy(-scrollX * TREE_SCROLL_STEP, -scrollY * TREE_SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && slotCycleArmed()) {
            confirmSlotOverwrite();
            return true;
        }
        // Esc inside a tree returns to the overview grid (the same as the back arrow), rather than closing
        // the whole screen; a second Esc from the grid then closes it via the default handler.
        if (event.isEscape() && mode == Mode.TREE && !skills.isEmpty()) {
            backToList();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (pressing && event.button() == InputConstants.MOUSE_BUTTON_LEFT && viewport != null) {
            if (!dragging) {
                double dx = event.x() - pressX;
                double dy = event.y() - pressY;
                if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                    dragging = true;   // moved far enough: this is a pan, not a click-to-buy
                }
            }
            if (dragging) {
                viewport.dragBy(dragX, dragY);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            // A press that never became a drag and ends on the same node it started on is a click.
            // Left-click buys the next rank; ability binding is right-click (see mouseClicked).
            if (pressing && !dragging && pressedNode != null && viewport != null
                    && viewport.nodeAt(geometry.nodes(), event.x(), event.y()) == pressedNode) {
                trySpend(pressedNode.perk());
            }
            pressing = false;
            dragging = false;
            pressedNode = null;
        }
        return super.mouseReleased(event);
    }

    /**
     * Steps an ability's action-bar binding to the next stop in order (slot 0..COUNT-1, then unbound, wrapping).
     * An empty slot (or the ability's own) binds immediately; a slot held by another ability arms an overwrite
     * that {@code Enter} confirms (see {@link #keyPressed}), so cycling never silently clobbers a binding.
     */
    private void cycleSlot(Identifier ability) {
        playClick();
        String id = ability.toString();
        long now = Util.getMillis();
        if (!id.equals(slotCycleAbility) || now > slotCycleUntil) {
            slotCycleAbility = id;
            slotCycleCommitted = currentData().slotOf(id);
            slotCycleCursor = slotCycleCommitted;
        }
        slotCycleCursor = nextSlotStop(slotCycleCursor);
        slotCycleUntil = now + SLOT_CYCLE_MS;
        if (slotCycleCursor < 0) {
            if (slotCycleCommitted >= 0) {
                NetworkDispatcher.sendSetAbilitySlot(slotCycleCommitted, Optional.empty());   // onto the unbound stop
                slotCycleCommitted = -1;
            }
            return;
        }
        String occupant = currentData().slotAbility(slotCycleCursor);
        if (occupant == null || occupant.equals(id)) {
            NetworkDispatcher.sendSetAbilitySlot(slotCycleCursor, Optional.of(ability));        // empty or own → take it
            slotCycleCommitted = slotCycleCursor;
        }
        // else: held by another ability → armed; the node tooltip prompts "held by X, Enter to replace"
    }

    /** Commits a pending overwrite (the cursor rests on a slot held by another ability). */
    private void confirmSlotOverwrite() {
        playClick();
        NetworkDispatcher.sendSetAbilitySlot(slotCycleCursor, Optional.of(Identifier.parse(slotCycleAbility)));
        slotCycleCommitted = slotCycleCursor;
        slotCycleUntil = Util.getMillis() + SLOT_CYCLE_MS;
    }

    private boolean slotCycleActive() {
        return slotCycleAbility != null && Util.getMillis() <= slotCycleUntil;
    }

    /** True while the cursor rests on a slot held by a DIFFERENT ability, so an Enter-to-replace is pending. */
    private boolean slotCycleArmed() {
        if (!slotCycleActive() || slotCycleCursor < 0) {
            return false;
        }
        String occupant = currentData().slotAbility(slotCycleCursor);
        return occupant != null && !occupant.equals(slotCycleAbility);
    }

    /** The next stop in the binding cycle: each slot in order, then the unbound stop (-1), wrapping. */
    private static int nextSlotStop(int cursor) {
        if (cursor < 0) {
            return 0;
        }
        return cursor + 1 < AbilitySlots.COUNT ? cursor + 1 : -1;
    }

    /** The display name of the perk that unlocks the given ability id, or the id itself as a fallback. */
    private static Component abilityDisplayName(String abilityId) {
        if (abilityId == null) {
            return Component.empty();
        }
        for (SkillDefinition def : SkillRegistry.all()) {
            for (SkillPerk perk : def.perks()) {
                Identifier unlocked = abilityOf(perk);
                if (unlocked != null && unlocked.toString().equals(abilityId)) {
                    return perkName(perk);
                }
            }
        }
        return Component.literal(abilityId);
    }

    /** Sends a spend packet iff the node is genuinely buyable; mirrors the server gate (which re-validates). */
    private void trySpend(SkillPerk perk) {
        SkillDefinition def = skills.get(selectedIndex);
        PlayerSkillData.SkillEntry entry = currentEntry(def.id());
        int rank = entry.rankOf(perk.id());
        if (rank >= perk.maxRank()) {
            return;
        }
        if (!perk.prerequisitesMet(pre -> entry.rankOf(pre) >= 1)) {
            return;
        }
        if (entry.availablePoints(def.totalCost()) < perk.costOfNextRank(rank)) {
            return;
        }
        // Wait for the prior buy on THIS node to sync (rank to advance) before sending another, so a
        // burst of clicks buys one rank per visible state change instead of overshooting. The timeout
        // means a lost/rejected packet (near-impossible, the gate mirrors the server) can't dead-end
        // the node. The server re-validates regardless.
        if (perk.id().equals(pendingBuyPerkId) && rank == pendingBuyRank
                && Util.getMillis() - pendingBuySentMs < BUY_RESEND_MS) {
            return;
        }
        pendingBuyPerkId = perk.id();
        pendingBuyRank = rank;
        pendingBuySentMs = Util.getMillis();
        NetworkDispatcher.sendUnlockSkillNode(def.id(), perk.id());
    }

    /** The clickable back region spans the arrow, its shaft, and the "Skills" label. */
    private boolean overBackArrow(double mouseX, double mouseY) {
        int x = leftPos + LEFT_ARROW_X;
        int w = ARROW_W + BACK_TAIL_LEN + 3 + font.width(backLabel());
        return hovers(mouseX, mouseY, x - ARROW_HIT_PAD, topPos + CYCLER_Y - ARROW_HIT_PAD,
                w + 2 * ARROW_HIT_PAD, ARROW_H + 2 * ARROW_HIT_PAD);
    }

    private static Component backLabel() {
        return Component.translatable("chronicles_leveling.screen.skills.title");
    }

    private boolean overTitle(double mouseX, double mouseY, Component title) {
        int w = (int) Math.ceil(font.width(title) * TITLE_SCALE);
        int h = (int) Math.ceil(font.lineHeight * TITLE_SCALE);
        return hovers(mouseX, mouseY, leftPos + IMAGE_WIDTH / 2 - w / 2, topPos + TITLE_Y, w, h);
    }

    private boolean overRespec(double mouseX, double mouseY, PlayerSkillData.SkillEntry entry) {
        int w = font.width(respecLabel(entry));
        return hovers(mouseX, mouseY, centeredX(w), footerTextY(), w, font.lineHeight);
    }

    /** "Confirm?" while an armed reset is pending (and points remain), else "Reset". */
    private Component respecLabel(PlayerSkillData.SkillEntry entry) {
        boolean armed = entry.spentPoints() > 0 && respecArmed();
        return Component.translatable(armed
                ? "chronicles_leveling.skill.tree.respec.confirm"
                : "chronicles_leveling.skill.tree.respec");
    }

    /** Armed (shows "Confirm?"); drives the label/tint regardless of the dwell grace. */
    private boolean respecArmed() {
        return Util.getMillis() < respecArmedUntil;
    }

    /** Armed AND past the dwell grace, so a deliberate (not reflexive double-) click can commit. */
    private boolean respecConfirmable() {
        long now = Util.getMillis();
        return now < respecArmedUntil && now >= respecArmedUntil - RESPEC_CONFIRM_MS + RESPEC_CONFIRM_GRACE_MS;
    }

    /** Left edge that horizontally centers {@code width} px of content in the current panel; the one centering formula. */
    private int centeredX(int width) {
        return leftPos + (panelW - width) / 2;
    }

    /** Half-open AABB hover test in screen space; the one place rectangular hover regions are decided. */
    private static boolean hovers(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
