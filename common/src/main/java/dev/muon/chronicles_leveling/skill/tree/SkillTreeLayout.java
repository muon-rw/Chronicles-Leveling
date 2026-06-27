package dev.muon.chronicles_leveling.skill.tree;

import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout constants + the two pure functions the screen calls: {@link #geometry} (player-independent,
 * cache once per skill) and {@link #project} (cheap, re-run when the player's skill data syncs).
 * Both are free of client-render imports, so the whole layout pipeline is headless-testable.
 */
public final class SkillTreeLayout {

    private SkillTreeLayout() {}

    /** Node icon-frame size in px (matches the vanilla advancement widget cell). */
    public static final int NODE = 26;
    /** Horizontal pitch between node columns (NODE + a gutter for connector routing). */
    public static final int COL_PITCH = 40;
    /** Vertical pitch between tiers. */
    public static final int ROW_PITCH = 40;
    /** Inner padding the viewport leaves around the content. */
    public static final int PAD = 8;

    /** Player-independent geometry for a skill under a layout strategy; cache this per skill. */
    public static TreeGeometry geometry(SkillDefinition definition, LayoutStrategy strategy) {
        return strategy.place(SkillTopology.of(definition));
    }

    /**
     * Layers per-player node state onto cached geometry. Pure and cheap (one pass over the nodes),
     * so the screen re-derives it from live data each frame rather than tracking a dirty flag.
     */
    public static LaidOutTree project(TreeGeometry geometry, PlayerSkillData.SkillEntry entry, int totalCost) {
        List<LaidOutTree.NodeView> views = new ArrayList<>(geometry.nodes().size());
        for (TreeGeometry.NodeBox box : geometry.nodes()) {
            int rank = entry.rankOf(box.perk().id());
            views.add(new LaidOutTree.NodeView(box, stateOf(box.perk(), rank, entry, totalCost), rank));
        }
        return new LaidOutTree(views, geometry.edges(), geometry.extent());
    }

    /**
     * Derives a node's render state from the player's rank in it, its prerequisites, and available
     * points (capped at {@code totalCost}). A multi-rank parent at rank ≥ 1 satisfies a child's prerequisite.
     */
    public static LaidOutTree.NodeState stateOf(SkillPerk perk, int rank, PlayerSkillData.SkillEntry entry, int totalCost) {
        if (rank >= perk.maxRank()) {
            return LaidOutTree.NodeState.MAXED;
        }
        if (rank >= 1) {
            return LaidOutTree.NodeState.UNLOCKED;
        }
        if (!perk.prerequisitesMet(pre -> entry.rankOf(pre) >= 1)) {
            return LaidOutTree.NodeState.LOCKED;
        }
        return entry.availablePoints(totalCost) >= perk.costOfNextRank(rank)
                ? LaidOutTree.NodeState.AVAILABLE
                : LaidOutTree.NodeState.LOCKED;
    }
}
