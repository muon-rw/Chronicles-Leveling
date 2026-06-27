package dev.muon.chronicles_leveling.skill.tree;

import dev.muon.chronicles_leveling.skill.perk.SkillPerk;

import java.util.List;

/**
 * Player-independent pixel geometry of a laid-out tree: where each node sits and the routed
 * connector segments between them, in canvas space normalized so the tree's top-left is (0,0).
 * A pure function of a {@link SkillTopology} and a {@link LayoutStrategy}, so it is computed once
 * per skill and cached; {@code SkillTreeLayout.project} layers per-player node state on top.
 */
public record TreeGeometry(List<NodeBox> nodes, List<Edge> edges, Extent extent) {

    public TreeGeometry {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** A node's box: top-left canvas coords; size is {@link SkillTreeLayout#NODE}. */
    public record NodeBox(SkillPerk perk, int x, int y) {
        public int centerX() {
            return x + SkillTreeLayout.NODE / 2;
        }
    }

    /** {@code segments} are pre-routed axis-aligned lines from {@code parent} to {@code child}. */
    public record Edge(NodeBox parent, NodeBox child, List<Line> segments) {
        public Edge {
            segments = List.copyOf(segments);
        }
    }

    public record Line(int x0, int y0, int x1, int y1) {}

    /** Bounding box of the laid-out content (normalized: {@code minX == minY == 0}). */
    public record Extent(int minX, int minY, int maxX, int maxY) {
        public int width() {
            return maxX - minX;
        }

        public int height() {
            return maxY - minY;
        }
    }
}
