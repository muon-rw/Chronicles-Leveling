package dev.muon.chronicles_leveling.skill.tree;

import java.util.List;

/**
 * The player-facing view model the screen draws: each node tagged with its per-player
 * {@link NodeState} and current rank, plus the (player-independent) edges + extent carried
 * through from {@link TreeGeometry}. A pure, cheap function of geometry + the player's
 * SkillEntry; the screen re-derives it from live (synced) data each frame, so it is always
 * current with no dirty-flag bookkeeping.
 */
public record LaidOutTree(List<NodeView> nodes, List<TreeGeometry.Edge> edges, TreeGeometry.Extent extent) {

    public LaidOutTree {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** A node with its resolved state + rank for rendering (rank drives the pip strip). */
    public record NodeView(TreeGeometry.NodeBox box, NodeState state, int rank) {}

    /** Rendering state of a node, derived from the player's ranks + available points. */
    public enum NodeState {
        /** Prerequisites unmet, or met but unaffordable. */
        LOCKED,
        /** Prerequisites met + affordable, not yet bought. */
        AVAILABLE,
        /** Owned (rank ≥ 1) with ranks still to buy. */
        UNLOCKED,
        /** Owned at max rank. */
        MAXED
    }
}
