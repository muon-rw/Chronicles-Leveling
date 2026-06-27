package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.skill.tree.SkillTreeLayout;
import dev.muon.chronicles_leveling.skill.tree.TreeGeometry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * A reusable scissor-clipped, click-drag-pannable canvas over a fixed viewport rect with NO zoom
 * (faithful pixel reproduction, like vanilla {@code AdvancementTab}: scroll is floored to integer
 * pixels and never scaled, so 1px connector lines stay crisp).
 *
 * <p>Owns the single integer scroll offset shared by BOTH rendering and hit-testing, the clamp,
 * and the exact draw sandwich, so the screen can't reorder scissor/transform or let the hover
 * bound drift from the clip bound. The content's top-left is (0,0) (the layout normalizes it), so
 * a content point {@code (cx,cy)} draws at {@code viewport + PAD + (cx,cy) - scroll}; hit-testing
 * is the same mapping inverted, with no scale term.
 */
public final class CanvasViewport {

    private static final int SCROLLBAR_THICKNESS = 2;
    private static final int SCROLLBAR_MIN_THUMB = 8;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final TreeGeometry.Extent extent;
    private double scrollX;
    private double scrollY;

    public CanvasViewport(int x, int y, int width, int height, TreeGeometry.Extent extent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.extent = extent;
        clamp();
    }

    /** Pans by a mouse-drag delta (screen px): the content follows the cursor. Re-clamps. */
    public void dragBy(double dx, double dy) {
        scrollX -= dx;
        scrollY -= dy;
        clamp();
    }

    /** Scrolls by a wheel-notch delta (content-space px). Re-clamps. */
    public void scrollBy(double dx, double dy) {
        scrollX += dx;
        scrollY += dy;
        clamp();
    }

    /** True if the screen point is inside the viewport rect: the single hit/clip bound (not scissor-state dependent). */
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** The node under the cursor, or {@code null}; gated by {@link #contains} first, then AABB in content space. */
    public TreeGeometry.NodeBox nodeAt(List<TreeGeometry.NodeBox> nodes, double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return null;
        }
        double cx = mouseX - x - SkillTreeLayout.PAD + Mth.floor(scrollX);
        double cy = mouseY - y - SkillTreeLayout.PAD + Mth.floor(scrollY);
        for (TreeGeometry.NodeBox box : nodes) {
            if (cx >= box.x() && cx < box.x() + SkillTreeLayout.NODE
                    && cy >= box.y() && cy < box.y() + SkillTreeLayout.NODE) {
                return box;
            }
        }
        return null;
    }

    /**
     * Runs {@code draw} (which paints in content coordinates) inside the scissor + translate
     * sandwich, in the exact, un-reorderable order. Integer-floored translate keeps lines crisp.
     */
    public void runClipped(GuiGraphicsExtractor graphics, Runnable draw) {
        graphics.enableScissor(x, y, x + width, y + height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + SkillTreeLayout.PAD - Mth.floor(scrollX), y + SkillTreeLayout.PAD - Mth.floor(scrollY));
        draw.run();
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    /**
     * Draws a thin track + thumb along each edge whose content overflows the viewport, so a tree
     * larger than the canvas reads as "there is more; drag to see it" and the thumb doubles as a
     * position indicator. Call in screen space AFTER {@link #runClipped} (it is a non-clipped
     * overlay, not part of the content). No-ops on an axis whose content already fits.
     */
    public void drawScrollbars(GuiGraphicsExtractor graphics, int trackColor, int thumbColor) {
        int paddedW = extent.width() + 2 * SkillTreeLayout.PAD;
        int paddedH = extent.height() + 2 * SkillTreeLayout.PAD;
        if (paddedH > height) {
            int barX = x + width - SCROLLBAR_THICKNESS;
            int thumb = Math.max(SCROLLBAR_MIN_THUMB, (int) ((long) height * height / paddedH));
            int thumbY = y + thumbOffset(scrollY, paddedH - height, height - thumb);
            graphics.fill(barX, y, barX + SCROLLBAR_THICKNESS, y + height, trackColor);
            graphics.fill(barX, thumbY, barX + SCROLLBAR_THICKNESS, thumbY + thumb, thumbColor);
        }
        if (paddedW > width) {
            int barY = y + height - SCROLLBAR_THICKNESS;
            int thumb = Math.max(SCROLLBAR_MIN_THUMB, (int) ((long) width * width / paddedW));
            int thumbX = x + thumbOffset(scrollX, paddedW - width, width - thumb);
            graphics.fill(x, barY, x + width, barY + SCROLLBAR_THICKNESS, trackColor);
            graphics.fill(thumbX, barY, thumbX + thumb, barY + SCROLLBAR_THICKNESS, thumbColor);
        }
    }

    /** Maps a clamped scroll position onto the thumb's pixel travel along its track. */
    private static int thumbOffset(double scroll, int scrollRange, int travel) {
        if (scrollRange <= 0 || travel <= 0) {
            return 0;
        }
        return (int) Math.round(travel * (Mth.clamp(scroll, 0.0, scrollRange) / scrollRange));
    }

    /** Center the content if it fits, else clamp scroll so it can't be dragged into the void. */
    private void clamp() {
        scrollX = clampAxis(scrollX, extent.width(), width);
        scrollY = clampAxis(scrollY, extent.height(), height);
    }

    private static double clampAxis(double scroll, int content, int viewport) {
        int padded = content + 2 * SkillTreeLayout.PAD;
        if (padded <= viewport) {
            return -(viewport - padded) / 2.0;   // negative scroll centers the content
        }
        return Mth.clamp(scroll, 0.0, (double) (padded - viewport));
    }
}
