package dev.muon.chronicles_leveling.skill.perk;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * One node in a skill tree. Topology is declared purely by {@link #prerequisites}
 * (the DAG); tiers and positions are derived by the layout, never authored. A
 * multi-rank node (Crit I/II/III) is ONE perk with {@code maxRank > 1} and a
 * rank-aware effect supplier, not several prerequisite-chained perks.
 *
 * <p><b>Rank contract for {@link #effectsAtRank}:</b> the returned effect <em>shape</em>
 * (which attributes / capabilities / abilities are targeted) must be the same across
 * ranks: the registry samples every rank and the applier derives its removal surface
 * from them. For an {@link AttributeEffect} the {@link Magnitude} must be rank-CONSTANT:
 * the applier multiplies the evaluated amount by rank, so baking rank into the magnitude
 * double-scales. Only a {@link CapabilityGrant}'s value should vary with rank.
 *
 * @param id            skill-local perk id (namespaced by {@link #owningSkill} in stable modifier ids)
 * @param owningSkill   the {@code Skills} id this perk belongs to (must equal its definition's id)
 * @param costPerRank   skill points per rank under the default flat cost curve
 * @param maxRank       1 for a single node; &gt;1 for a multi-rank node (validated ≥ 1 at freeze)
 * @param prerequisites perk ids (same skill) candidates this node depends on; each must be at rank ≥ 1
 * @param requiredPrerequisites how MANY of {@link #prerequisites} must be owned to unlock (default: all).
 *                      {@code < prerequisites.size()} models a converging "any K of N" node
 * @param orderHint     soft within-tier sort tiebreak (layout advice, never geometry); usually empty
 * @param anchorUnderParents layout: keep this node centered under its parents, so a single shared child
 *                      (e.g. a capstone above several converging finishers) can't pull it toward its siblings
 * @param effectsAtRank rank → the COMPLETE effect set when the perk is AT that rank
 * @param costCurve     rank → cumulative point cost to reach that rank (default {@code rank * costPerRank})
 */
public record SkillPerk(
        String id,
        String owningSkill,
        int costPerRank,
        int maxRank,
        Set<String> prerequisites,
        int requiredPrerequisites,
        OptionalInt orderHint,
        boolean anchorUnderParents,
        IntFunction<List<PerkEffect>> effectsAtRank,
        IntUnaryOperator costCurve) {

    public SkillPerk {
        prerequisites = Set.copyOf(prerequisites);
    }

    /** Whether enough prerequisites are owned (at least {@link #requiredPrerequisites} test true). */
    public boolean prerequisitesMet(Predicate<String> owned) {
        return ownedPrerequisites(owned) >= requiredPrerequisites;
    }

    /** How many more prerequisites must be acquired before this unlocks (0 once met). */
    public int prerequisitesStillNeeded(Predicate<String> owned) {
        return Math.max(0, requiredPrerequisites - ownedPrerequisites(owned));
    }

    private int ownedPrerequisites(Predicate<String> owned) {
        int have = 0;
        for (String pre : prerequisites) {
            if (owned.test(pre)) {
                have++;
            }
        }
        return have;
    }

    /** The complete effect set when this perk is at the given rank. */
    public List<PerkEffect> effectsAtRank(int rank) {
        return effectsAtRank.apply(rank);
    }

    /** Cumulative skill-point cost to reach the given rank. */
    public int costThroughRank(int rank) {
        return costCurve.applyAsInt(rank);
    }

    /** Marginal cost to advance from {@code currentRank} to the next rank. */
    public int costOfNextRank(int currentRank) {
        return costThroughRank(currentRank + 1) - costThroughRank(currentRank);
    }
}
