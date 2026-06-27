package dev.muon.chronicles_leveling.skill.tree;

import dev.muon.chronicles_leveling.skill.perk.SkillPerk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The default branching layout (Sugiyama-lite). Tiers come from longest-path layering
 * ({@link SkillTopology#tierOf}); within a tier, nodes are ordered by the mean slot of their
 * parents (one stable down-sweep that reduces edge crossings), tie-broken by {@code orderHint}
 * then declaration index. Coordinates are then assigned by iterated barycenter passes with a
 * min-gap isotonic placement, so parents sit centered above their children (and children centered
 * below their parents) rather than a sparse tier stacking at the left edge; finally normalized so
 * the tree's top-left is (0,0). Connectors route as axis-aligned elbow segments down the gutter.
 *
 * <p>Deterministic (fixed iteration count, no randomness) and O(iterations·(V+E)); re-derives
 * identically across reloads and both loaders. (This intentionally trades the old left-justified
 * coordinate stability for proper tree centering, an owner-requested correctness fix.)
 */
public final class LayeredLayout implements LayoutStrategy {

    public static final LayeredLayout INSTANCE = new LayeredLayout();

    /** Barycenter centering passes (alternating down/up); even count ends on an UP pass. */
    private static final int COORD_ITERATIONS = 8;

    private LayeredLayout() {}

    @Override
    public TreeGeometry place(SkillTopology topology) {
        List<SkillPerk> nodes = topology.nodes();
        Map<String, Integer> tierOf = topology.tierOf();

        Map<String, Integer> declIndex = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            declIndex.put(nodes.get(i).id(), i);
        }

        int maxTier = 0;
        for (int tier : tierOf.values()) {
            maxTier = Math.max(maxTier, tier);
        }
        List<List<SkillPerk>> byTier = new ArrayList<>();
        for (int t = 0; t <= maxTier; t++) {
            byTier.add(new ArrayList<>());
        }
        for (SkillPerk perk : nodes) {
            byTier.get(tierOf.get(perk.id())).add(perk);
        }

        // Slot assignment: tier 0 keeps declaration order; each lower tier sorts by the mean slot
        // of its parents (already placed in the tier above), then orderHint, then declaration index.
        Map<String, Integer> slotOf = new HashMap<>();
        for (int t = 0; t <= maxTier; t++) {
            boolean root = t == 0;
            byTier.get(t).sort(Comparator
                    .comparingDouble((SkillPerk p) -> root ? declIndex.get(p.id()) : barycenter(p, slotOf, declIndex))
                    .thenComparingInt(p -> p.orderHint().orElse(Integer.MAX_VALUE))
                    .thenComparingInt(p -> declIndex.get(p.id())));
            List<SkillPerk> tier = byTier.get(t);
            for (int slot = 0; slot < tier.size(); slot++) {
                slotOf.put(tier.get(slot).id(), slot);
            }
        }

        // Within-skill prerequisite adjacency (existing perks only) for the centering passes.
        Map<String, List<String>> parents = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (SkillPerk perk : nodes) {
            parents.put(perk.id(), new ArrayList<>());
            children.put(perk.id(), new ArrayList<>());
        }
        for (SkillPerk perk : nodes) {
            for (String pre : perk.prerequisites()) {
                if (tierOf.containsKey(pre)) {
                    parents.get(perk.id()).add(pre);
                    children.get(pre).add(perk.id());
                }
            }
        }

        // Coordinate assignment. Seed left-justified by slot, then iterate barycenter passes: DOWN aligns
        // each tier to its parents, UP aligns each tier to its children, each pass resolving in-tier overlaps
        // via the min-gap isotonic placement below. The result centers parents over children (and children
        // under parents) instead of stacking a sparse tier at the left edge. Ends on an UP pass so parents
        // sit centered above their children. Deterministic (fixed iteration count, no randomness).
        Map<String, Double> x = new HashMap<>();
        for (SkillPerk perk : nodes) {
            x.put(perk.id(), (double) slotOf.get(perk.id()) * SkillTreeLayout.COL_PITCH);
        }
        for (int iter = 0; iter < COORD_ITERATIONS; iter++) {
            boolean downward = (iter & 1) == 0;
            for (int step = 0; step <= maxTier; step++) {
                List<SkillPerk> tier = byTier.get(downward ? step : maxTier - step);
                if (tier.isEmpty()) {
                    continue;
                }
                double[] desired = new double[tier.size()];
                for (int i = 0; i < tier.size(); i++) {
                    SkillPerk node = tier.get(i);
                    // Anchored nodes align to their parents even on up-sweeps, so a single shared child (a
                    // capstone) can't pull converging finishers together into the center.
                    boolean toParents = downward || node.anchorUnderParents();
                    List<String> neighbours = toParents ? parents.get(node.id()) : children.get(node.id());
                    desired[i] = neighbours.isEmpty() ? x.get(node.id()) : mean(neighbours, x);
                }
                double[] placed = centerWithMinGap(desired, SkillTreeLayout.COL_PITCH);
                for (int i = 0; i < tier.size(); i++) {
                    x.put(tier.get(i).id(), placed[i]);
                }
            }
        }

        double minX = Double.MAX_VALUE;
        for (double v : x.values()) {
            minX = Math.min(minX, v);
        }

        Map<String, TreeGeometry.NodeBox> boxById = new HashMap<>();
        List<TreeGeometry.NodeBox> boxes = new ArrayList<>(nodes.size());
        int maxX = 0;
        int maxY = 0;
        for (SkillPerk perk : nodes) {
            int px = (int) Math.round(x.get(perk.id()) - minX);
            int py = tierOf.get(perk.id()) * SkillTreeLayout.ROW_PITCH;
            TreeGeometry.NodeBox box = new TreeGeometry.NodeBox(perk, px, py);
            boxById.put(perk.id(), box);
            boxes.add(box);
            maxX = Math.max(maxX, px + SkillTreeLayout.NODE);
            maxY = Math.max(maxY, py + SkillTreeLayout.NODE);
        }

        List<TreeGeometry.Edge> edges = new ArrayList<>();
        for (TreeGeometry.NodeBox child : boxes) {
            for (String prerequisite : child.perk().prerequisites()) {
                TreeGeometry.NodeBox parent = boxById.get(prerequisite);
                if (parent != null) {
                    edges.add(new TreeGeometry.Edge(parent, child, elbow(parent, child)));
                }
            }
        }

        return new TreeGeometry(boxes, edges, new TreeGeometry.Extent(0, 0, maxX, maxY));
    }

    private static double barycenter(SkillPerk perk, Map<String, Integer> slotOf, Map<String, Integer> declIndex) {
        double sum = 0;
        int n = 0;
        for (String prerequisite : perk.prerequisites()) {
            Integer slot = slotOf.get(prerequisite);
            if (slot != null) {
                sum += slot;
                n++;
            }
        }
        return n == 0 ? declIndex.get(perk.id()) : sum / n;
    }

    private static double mean(List<String> ids, Map<String, Double> x) {
        double sum = 0;
        for (String id : ids) {
            sum += x.get(id);
        }
        return sum / ids.size();
    }

    /**
     * Places {@code desired.length} nodes (in their given order) as close to their {@code desired} x as
     * possible while keeping consecutive nodes at least {@code gap} apart. This is the min-gap-ordered
     * least-squares placement, solved by isotonic regression (pool-adjacent-violators) on
     * {@code desired[i] - i*gap}: a cluster of nodes all wanting the same spot is spread symmetrically
     * around it (centered), not stacked from one side.
     */
    private static double[] centerWithMinGap(double[] desired, int gap) {
        int n = desired.length;
        double[] value = new double[n];   // pooled value per block (used as a stack)
        double[] sum = new double[n];
        int[] count = new int[n];
        int blocks = 0;
        for (int i = 0; i < n; i++) {
            value[blocks] = desired[i] - (double) i * gap;
            sum[blocks] = value[blocks];
            count[blocks] = 1;
            while (blocks > 0 && value[blocks - 1] > value[blocks]) {   // merge violating adjacent blocks
                sum[blocks - 1] += sum[blocks];
                count[blocks - 1] += count[blocks];
                value[blocks - 1] = sum[blocks - 1] / count[blocks - 1];
                blocks--;
            }
            blocks++;
        }
        double[] pos = new double[n];
        int idx = 0;
        for (int b = 0; b < blocks; b++) {
            for (int k = 0; k < count[b]; k++) {
                pos[idx] = value[b] + (double) idx * gap;   // re-add the i*gap offset removed above
                idx++;
            }
        }
        return pos;
    }

    /** Three-segment orthogonal elbow from the parent's bottom-center to the child's top-center. */
    private static List<TreeGeometry.Line> elbow(TreeGeometry.NodeBox parent, TreeGeometry.NodeBox child) {
        int px = parent.centerX();
        int cx = child.centerX();
        int py = parent.y() + SkillTreeLayout.NODE;
        int cy = child.y();
        int midY = (py + cy) / 2;
        return List.of(
                new TreeGeometry.Line(px, py, px, midY),
                new TreeGeometry.Line(px, midY, cx, midY),
                new TreeGeometry.Line(cx, midY, cx, cy));
    }
}
