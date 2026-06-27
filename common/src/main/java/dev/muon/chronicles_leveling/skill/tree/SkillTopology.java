package dev.muon.chronicles_leveling.skill.tree;

import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pure prerequisite-DAG view of a skill, with each perk's tier derived by longest-path
 * layering. A {@link LayoutStrategy} turns this into pixel geometry.
 *
 * <p>{@code of(...)} assumes the definition is acyclic (the registry freeze rejects cycles), so
 * the longest-path recursion terminates.
 */
public record SkillTopology(List<SkillPerk> nodes, Map<String, Integer> tierOf, List<TopoEdge> edges) {

    public SkillTopology {
        nodes = List.copyOf(nodes);
        tierOf = Map.copyOf(tierOf);
        edges = List.copyOf(edges);
    }

    /** A directed prerequisite edge (parent must be unlocked before child). */
    public record TopoEdge(String parentId, String childId) {}

    public static SkillTopology of(SkillDefinition definition) {
        Map<String, SkillPerk> byId = new HashMap<>();
        for (SkillPerk perk : definition.perks()) {
            byId.put(perk.id(), perk);
        }
        Map<String, Integer> tierOf = new HashMap<>();
        for (SkillPerk perk : definition.perks()) {
            tier(perk.id(), byId, tierOf);
        }
        List<TopoEdge> edges = new ArrayList<>();
        for (SkillPerk perk : definition.perks()) {
            for (String prerequisite : perk.prerequisites()) {
                edges.add(new TopoEdge(prerequisite, perk.id()));
            }
        }
        return new SkillTopology(definition.perks(), tierOf, edges);
    }

    /** Longest path from a root: {@code 0} if no prerequisites, else {@code 1 + max(prereq tier)}. */
    private static int tier(String id, Map<String, SkillPerk> byId, Map<String, Integer> tierOf) {
        Integer cached = tierOf.get(id);
        if (cached != null) {
            return cached;
        }
        int t = 0;
        for (String prerequisite : byId.get(id).prerequisites()) {
            if (byId.containsKey(prerequisite)) {
                t = Math.max(t, 1 + tier(prerequisite, byId, tierOf));
            }
        }
        tierOf.put(id, t);
        return t;
    }
}
