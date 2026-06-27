package dev.muon.chronicles_leveling.skill.perk;

import dev.muon.chronicles_leveling.skill.ability.SkillAbility;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/**
 * A skill's full definition: its display name, its perk tree (a flat list of nodes
 * whose edges are the perks' prerequisites), and its active abilities. Built once at
 * registry-freeze time via {@link #builder} and immutable thereafter.
 *
 * <p>The builder is the authoring surface: a skill is declared as a dependency story,
 * not a grid of coordinates:
 * <pre>{@code
 * SkillDefinition.builder(Skills.WEAPONRY, display)
 *     .perk("crit_focus").cost(1)
 *         .effect(attr(MELEE_CRIT_CHANCE, ADD_VALUE, perLevel(0.005, 0.50)))
 *     .perk("rupture").requires("crit_focus").cost(1).maxRank(3)
 *         .effectsAtRank(rank -> List.of(grant(BLEED_ON_HIT, new Bleed(rank, 60))))
 *     .perk("whirlwind_training").requires("rupture").cost(2)
 *         .effect(unlocks(WhirlwindAbility.ID))
 *     .ability(new WhirlwindAbility())
 *     .build();
 * }</pre>
 */
public record SkillDefinition(String id, Component display, Optional<Component> description,
                              List<SkillPerk> perks, List<SkillAbility> abilities) {

    public SkillDefinition {
        perks = List.copyOf(perks);
        abilities = List.copyOf(abilities);
    }

    /** Total skill points to fully complete this tree: every perk bought through its max rank. */
    public int totalCost() {
        int sum = 0;
        for (SkillPerk p : perks) {
            sum += p.costThroughRank(p.maxRank());
        }
        return sum;
    }

    /** Finds a perk by id, or {@code null} if this skill has no such perk. */
    public SkillPerk perk(String perkId) {
        for (SkillPerk p : perks) {
            if (p.id().equals(perkId)) {
                return p;
            }
        }
        return null;
    }

    public static Builder builder(String id, Component display) {
        return new Builder(id, display);
    }

    /**
     * Fluent builder. {@code perk(id)} opens a node; the chained
     * {@code cost/maxRank/requires/order/effect/effectsAtRank} calls configure the node
     * currently being built; the next {@code perk(...)}, {@code ability(...)}, or
     * {@code build()} commits it. Common perks need no positioning; tier and column
     * are derived from prerequisites by the layout.
     */
    public static final class Builder {

        private final String id;
        private final Component display;
        private Optional<Component> description = Optional.empty();
        private final List<SkillPerk> perks = new ArrayList<>();
        private final List<SkillAbility> abilities = new ArrayList<>();
        private PerkDraft pending;

        private Builder(String id, Component display) {
            this.id = id;
            this.display = display;
        }

        /**
         * Sets the one-line skill description shown in the tree screen's header tooltip. Optional and
         * author-supplied like {@link #display}, so addon skills get a description the same way core
         * ones do, with no reliance on a Chronicles-namespaced lang key.
         */
        public Builder description(Component description) {
            this.description = Optional.of(description);
            return this;
        }

        public Builder perk(String perkId) {
            commitPending();
            pending = new PerkDraft(perkId);
            return this;
        }

        public Builder cost(int costPerRank) {
            requirePending().costPerRank = costPerRank;
            return this;
        }

        public Builder maxRank(int maxRank) {
            requirePending().maxRank = maxRank;
            return this;
        }

        public Builder requires(String... prerequisites) {
            requirePending().prerequisites.addAll(List.of(prerequisites));
            return this;
        }

        /**
         * Requires only {@code count} of the {@link #requires} prerequisites, a converging "any K of N"
         * node (e.g. any 2 of three branch capstones). Default (unset) is all prerequisites.
         */
        public Builder requireAny(int count) {
            requirePending().requiredPrereqs = count;
            return this;
        }

        public Builder order(int orderHint) {
            requirePending().orderHint = OptionalInt.of(orderHint);
            return this;
        }

        /**
         * Layout hint: keep this node centered under its prerequisites even if a single shared child (a capstone
         * above several converging finishers) would otherwise drag it toward its siblings in the center.
         */
        public Builder anchorUnderParents() {
            requirePending().anchorUnderParents = true;
            return this;
        }

        /** Overrides the flat per-rank cost with a cumulative cost curve (rank → total points to reach it). */
        public Builder costCurve(IntUnaryOperator costThroughRank) {
            requirePending().costCurve = costThroughRank;
            return this;
        }

        /** Adds one effect granted at every rank. May be called more than once to add several. */
        public Builder effect(PerkEffect effect) {
            requirePending().flatEffects.add(effect);
            return this;
        }

        /** Sets rank-aware effects for a multi-rank node: {@code rank → effects at that rank}. */
        public Builder effectsAtRank(IntFunction<List<PerkEffect>> effects) {
            requirePending().rankEffects = effects;
            return this;
        }

        public Builder ability(SkillAbility ability) {
            commitPending();
            abilities.add(ability);
            return this;
        }

        public SkillDefinition build() {
            commitPending();
            return new SkillDefinition(id, display, description, perks, abilities);
        }

        private PerkDraft requirePending() {
            if (pending == null) {
                throw new IllegalStateException("call perk(id) before configuring a perk");
            }
            return pending;
        }

        private void commitPending() {
            if (pending != null) {
                perks.add(pending.toPerk(id));
                pending = null;
            }
        }

        /** Mutable scratch for one perk; {@code effect()} and {@code effectsAtRank()} are mutually exclusive. */
        private static final class PerkDraft {
            final String perkId;
            int costPerRank = 1;
            int maxRank = 1;
            final Set<String> prerequisites = new LinkedHashSet<>();
            int requiredPrereqs = -1;   // -1 = all prerequisites (the default)
            OptionalInt orderHint = OptionalInt.empty();
            boolean anchorUnderParents = false;
            final List<PerkEffect> flatEffects = new ArrayList<>();
            IntFunction<List<PerkEffect>> rankEffects;
            IntUnaryOperator costCurve;

            PerkDraft(String perkId) {
                this.perkId = perkId;
            }

            SkillPerk toPerk(String owningSkill) {
                IntFunction<List<PerkEffect>> effects;
                if (rankEffects != null) {
                    if (!flatEffects.isEmpty()) {
                        throw new IllegalStateException(
                                "perk '" + perkId + "' uses both effect() and effectsAtRank(); pick one");
                    }
                    effects = rankEffects;
                } else {
                    List<PerkEffect> fixed = List.copyOf(flatEffects);
                    effects = rank -> fixed;
                }
                final int flatCost = costPerRank;
                IntUnaryOperator curve = (costCurve != null) ? costCurve : (rank -> Math.max(0, rank) * flatCost);
                int required = requiredPrereqs < 0 ? prerequisites.size() : requiredPrereqs;
                return new SkillPerk(perkId, owningSkill, costPerRank, maxRank, prerequisites, required, orderHint,
                        anchorUnderParents, effects, curve);
            }
        }
    }
}
