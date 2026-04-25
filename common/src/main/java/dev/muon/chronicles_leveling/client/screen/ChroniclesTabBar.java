package dev.muon.chronicles_leveling.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The horizontal row of three tab buttons (Inventory / Stats / Attributes) that
 * sits above each Chronicles screen and the vanilla {@link
 * net.minecraft.client.gui.screens.inventory.InventoryScreen} (via mixin).
 *
 * <p>Art originally from PlayerEx (Licensed MIT). Layout assumptions:
 * <ul>
 *   <li>{@code tab.png} is 256×256 with 28×32 cells in a 4-row grid.
 *       Row 0 = top-row active, row 1 = top-row inactive.
 *       (Rows 2–3 are the bottom-anchored variants — unused here.)</li>
 *   <li>The bar anchors to {@code (panelLeft, panelTop)} and renders tabs at
 *       {@code y = panelTop - TAB_HEIGHT}, so the bottom of an active tab is
 *       flush with the panel top.</li>
 *   <li>Each tab carries a 16×16 icon from its {@link ChroniclesTab#icon()},
 *       centered horizontally and biased vertically so it sits inside the
 *       visible part of the cell (active tabs show more, so the icon hugs
 *       lower; inactive tabs hide their bottom edge under the panel, so the
 *       icon sits a bit higher).</li>
 * </ul>
 *
 */
public class ChroniclesTabBar implements Renderable, GuiEventListener, NarratableEntry {

    private static final int TAB_WIDTH = 28;
    private static final int TAB_HEIGHT = 32;
    /**
     * Vertical nudge applied to every tab cell. PlayerEx's art doesn't sit cell-flush
     * with the panel top; this 3px shift lines the tab base up with the panel edge.
     */
    private static final int TAB_VERTICAL_OFFSET = 4;
    private static final int TEXTURE_SIZE = 256;
    private static final int ICON_SIZE = 16;

    /**
     * Texture rows (in pixels). The PlayerEx art puts the elevated/active variant
     * on row 1 and the recessed/inactive variant on row 0, contrary to the vanilla
     * creative-tab convention.
     */
    private static final int ACTIVE_V = 32;
    private static final int INACTIVE_V = 0;

    /** Where the icon rides inside the cell; inactive shifts down to compensate for the hidden bottom. */
    private static final int ICON_OFFSET_X = (TAB_WIDTH - ICON_SIZE) / 2;
    private static final int ICON_OFFSET_Y_ACTIVE = 8;
    private static final int ICON_OFFSET_Y_INACTIVE = 12;

    private final int panelLeft;
    private final int panelTop;
    private boolean focused;

    public ChroniclesTabBar(int panelLeft, int panelTop) {
        this.panelLeft = panelLeft;
        this.panelTop = panelTop;
    }

    public static int totalWidth() {
        return ChroniclesTab.values().length * TAB_WIDTH;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Screen current = Minecraft.getInstance().screen;
        ChroniclesTab[] tabs = ChroniclesTab.values();

        for (int i = 0; i < tabs.length; i++) {
            int tabX = panelLeft + i * TAB_WIDTH;
            int tabY = panelTop - TAB_HEIGHT + TAB_VERTICAL_OFFSET;
            boolean active = tabs[i].isActive(current);

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    ChroniclesTextures.TAB,
                    tabX, tabY,
                    i * TAB_WIDTH, active ? ACTIVE_V : INACTIVE_V,
                    TAB_WIDTH, TAB_HEIGHT,
                    TEXTURE_SIZE, TEXTURE_SIZE
            );

            int iconX = tabX + ICON_OFFSET_X;
            int iconY = tabY + (active ? ICON_OFFSET_Y_ACTIVE : ICON_OFFSET_Y_INACTIVE);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tabs[i].icon(),
                    iconX, iconY,
                    0, 0,
                    ICON_SIZE, ICON_SIZE,
                    ICON_SIZE, ICON_SIZE
            );
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        ChroniclesTab[] tabs = ChroniclesTab.values();
        for (int i = 0; i < tabs.length; i++) {
            int tabX = panelLeft + i * TAB_WIDTH;
            if (event.x() >= tabX && event.x() < tabX + TAB_WIDTH) {
                tabs[i].open();
                return true;
            }
        }
        return false;
    }

    /**
     * AbstractContainerEventHandler routes clicks via {@code getChildAt}, which
     * iterates children and picks the first whose {@code isMouseOver} returns
     * true. Without this override the default returns false, our widget is
     * invisible to the router, and clicks fall through to the screen background.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int barTop = panelTop - TAB_HEIGHT + TAB_VERTICAL_OFFSET;
        int barBottom = panelTop + TAB_VERTICAL_OFFSET;
        return mouseY >= barTop && mouseY < barBottom
                && mouseX >= panelLeft && mouseX < panelLeft + totalWidth();
    }

    @Override public void setFocused(boolean focused) { this.focused = focused; }
    @Override public boolean isFocused() { return focused; }
    @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
    @Override public void updateNarration(NarrationElementOutput out) {}
}
