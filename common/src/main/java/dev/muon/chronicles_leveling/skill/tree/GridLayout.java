package dev.muon.chronicles_leveling.skill.tree;

import dev.muon.chronicles_leveling.skill.perk.SkillPerk;

import java.util.ArrayList;
import java.util.List;

/**
 * The fallback layout: a plain fixed grid, ignoring prerequisite edges (no connectors).
 * Interchangeable with {@link LayeredLayout} over the same view model, so switching a skill to
 * the grid is a one-line {@code .layout(...)} choice with no screen changes: the
 * proven-achievable shape if the branching canvas ever proves fiddly for a given tree.
 */
public final class GridLayout implements LayoutStrategy {

    public static final GridLayout INSTANCE = new GridLayout(5);

    private final int columns;

    public GridLayout(int columns) {
        this.columns = Math.max(1, columns);
    }

    @Override
    public TreeGeometry place(SkillTopology topology) {
        List<SkillPerk> nodes = topology.nodes();
        List<TreeGeometry.NodeBox> boxes = new ArrayList<>(nodes.size());
        int maxX = 0;
        int maxY = 0;
        for (int i = 0; i < nodes.size(); i++) {
            int x = (i % columns) * SkillTreeLayout.COL_PITCH;
            int y = (i / columns) * SkillTreeLayout.ROW_PITCH;
            boxes.add(new TreeGeometry.NodeBox(nodes.get(i), x, y));
            maxX = Math.max(maxX, x + SkillTreeLayout.NODE);
            maxY = Math.max(maxY, y + SkillTreeLayout.NODE);
        }
        return new TreeGeometry(boxes, List.of(), new TreeGeometry.Extent(0, 0, maxX, maxY));
    }
}
