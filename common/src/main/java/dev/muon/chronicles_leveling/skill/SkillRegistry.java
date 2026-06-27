package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.ability.SkillAbility;
import dev.muon.chronicles_leveling.skill.perk.AbilityUnlock;
import dev.muon.chronicles_leveling.skill.perk.AttributeEffect;
import dev.muon.chronicles_leveling.skill.perk.CapabilityGrant;
import dev.muon.chronicles_leveling.skill.perk.PerkEffect;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import dev.muon.chronicles_leveling.stat.ModStats;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The frozen catalog of every skill definition. Built at common setup (core's twelve
 * trees plus any addon contributions) then {@link #freeze}d (validated + made
 * immutable) before the first recompute or screen open. Lookups are O(1).
 *
 * <p>Freeze is the single fail-fast gate: a malformed tree (duplicate perk id, dangling
 * prerequisite, prerequisite cycle, an ability unlock with no matching ability, an
 * attribute effect aimed at a stat attribute, or a capability whose value its own fold
 * rejects) crashes the game at load with a clear message rather than corrupting a
 * player mid-session. The stat-attribute check compares ids only, so it never needs the
 * attribute Holders to be published yet.
 */
public final class SkillRegistry {

    private SkillRegistry() {}

    private static final Map<String, SkillDefinition> WORKING = new LinkedHashMap<>();
    private static final Set<Identifier> STAT_ATTRIBUTES = statAttributeIds();

    private static volatile Map<String, SkillDefinition> frozen;
    private static volatile Map<Identifier, SkillAbility> abilityIndex;

    /** Registers a skill definition. Fails if the registry is already frozen. */
    public static void register(SkillDefinition definition) {
        if (frozen != null) {
            throw new IllegalStateException("SkillRegistry is frozen; register during the contribution phase"
                    + " (Fabric: chronicles_leveling:skills entrypoint; NeoForge: RegisterSkillContributionsEvent on the mod bus)");
        }
        if (WORKING.containsKey(definition.id())) {
            throw new IllegalStateException("Duplicate skill definition for '" + definition.id() + "'");
        }
        WORKING.put(definition.id(), definition);
    }

    /**
     * Appends perks and/or abilities onto an already-registered skill; the addon path
     * for extending a core tree. The merged definition is validated by {@link #freeze}
     * like any other (duplicate perk ids, dangling prerequisites, cycles all caught),
     * so this is pure pre-freeze assembly.
     */
    public static void contribute(String skillId, List<SkillPerk> extraPerks, List<SkillAbility> extraAbilities) {
        if (frozen != null) {
            throw new IllegalStateException("SkillRegistry is frozen; contribute during the contribution phase"
                    + " (Fabric: chronicles_leveling:skills entrypoint; NeoForge: RegisterSkillContributionsEvent on the mod bus)");
        }
        SkillDefinition existing = WORKING.get(skillId);
        if (existing == null) {
            throw new IllegalStateException("Cannot contribute to unknown skill '" + skillId + "'");
        }
        List<SkillPerk> perks = new ArrayList<>(existing.perks());
        perks.addAll(extraPerks);
        List<SkillAbility> abilities = new ArrayList<>(existing.abilities());
        abilities.addAll(extraAbilities);
        WORKING.put(skillId, new SkillDefinition(skillId, existing.display(), existing.description(), perks, abilities));
    }

    /** Validates every definition and makes the registry immutable. Throws if called twice. */
    public static void freeze() {
        if (frozen != null) {
            throw new IllegalStateException("SkillRegistry already frozen");
        }
        Map<Identifier, SkillAbility> abilities = indexAbilities();
        Map<String, SkillCapability<?>> capabilityIds = new HashMap<>();
        for (SkillDefinition def : WORKING.values()) {
            validate(def, abilities, capabilityIds);
        }
        abilityIndex = Map.copyOf(abilities);
        frozen = Map.copyOf(WORKING);
        ChroniclesLeveling.LOG.info("Froze SkillRegistry: {} skills, {} abilities", frozen.size(), abilityIndex.size());
    }

    public static boolean isFrozen() {
        return frozen != null;
    }

    /** The definition for a skill id, or {@code null} if unknown. */
    public static SkillDefinition get(String skillId) {
        return frozenMap().get(skillId);
    }

    public static Collection<SkillDefinition> all() {
        return frozenMap().values();
    }

    /** Resolves an ability by its id across all skills, or {@code null} if unknown. */
    public static SkillAbility ability(Identifier abilityId) {
        requireFrozen();
        return abilityIndex.get(abilityId);
    }

    // --- internals ---

    private static Map<String, SkillDefinition> frozenMap() {
        requireFrozen();
        return frozen;
    }

    private static void requireFrozen() {
        if (frozen == null) {
            throw new IllegalStateException("SkillRegistry not yet frozen");
        }
    }

    private static Map<Identifier, SkillAbility> indexAbilities() {
        Map<Identifier, SkillAbility> index = new HashMap<>();
        for (SkillDefinition def : WORKING.values()) {
            for (SkillAbility ability : def.abilities()) {
                if (!ability.owningSkill().equals(def.id())) {
                    throw new IllegalStateException("Ability '" + ability.id() + "' declares owningSkill '"
                            + ability.owningSkill() + "' but is registered under skill '" + def.id() + "'");
                }
                if (index.put(ability.id(), ability) != null) {
                    throw new IllegalStateException("Duplicate ability id '" + ability.id() + "'");
                }
            }
        }
        return index;
    }

    private static Set<Identifier> statAttributeIds() {
        Set<Identifier> ids = new HashSet<>();
        for (ModStats.Entry stat : ModStats.ALL) {
            ids.add(ChroniclesLeveling.id(stat.id()));
        }
        return Set.copyOf(ids);
    }

    private static void validate(SkillDefinition def, Map<Identifier, SkillAbility> abilities,
                                 Map<String, SkillCapability<?>> capabilityIds) {
        Map<String, SkillPerk> byId = new HashMap<>();
        for (SkillPerk perk : def.perks()) {
            if (!perk.owningSkill().equals(def.id())) {
                throw fail(def, "perk '" + perk.id() + "' declares owningSkill '" + perk.owningSkill()
                        + "', which is not this skill");
            }
            if (perk.maxRank() < 1) {
                throw fail(def, "perk '" + perk.id() + "' has maxRank " + perk.maxRank() + " (must be >= 1)");
            }
            if (perk.costPerRank() < 0) {
                throw fail(def, "perk '" + perk.id() + "' has negative costPerRank " + perk.costPerRank());
            }
            if (byId.put(perk.id(), perk) != null) {
                throw fail(def, "duplicate perk id '" + perk.id() + "'");
            }
        }
        for (SkillPerk perk : def.perks()) {
            for (String pre : perk.prerequisites()) {
                if (!byId.containsKey(pre)) {
                    throw fail(def, "perk '" + perk.id() + "' requires unknown perk '" + pre + "'");
                }
            }
            int required = perk.requiredPrerequisites();
            int available = perk.prerequisites().size();
            if (required < 0 || required > available) {
                throw fail(def, "perk '" + perk.id() + "' requires " + required + " of " + available
                        + " prerequisites (requiredPrerequisites must be between 0 and the prerequisite count)");
            }
            if (available > 0 && required < 1) {
                throw fail(def, "perk '" + perk.id() + "' has prerequisites but requires 0 of them"
                        + " (a node with prerequisites must require at least one)");
            }
            validateEffects(def, perk, abilities, capabilityIds);
            checkCostCurve(def, perk);
        }
        checkNoCycle(def, byId);
    }

    /**
     * A perk's cumulative cost curve must start at 0 and never decrease across ranks. The default flat
     * curve satisfies this trivially; an addon's {@code costCurve} override does not have to, and the
     * spend gate, {@code withPerkRank}, and {@code reconcile} all trust the curve's marginal/cumulative
     * values; a decreasing curve would let a rank-up refund points. Caught here at the one fail-fast gate.
     */
    private static void checkCostCurve(SkillDefinition def, SkillPerk perk) {
        int atZero = perk.costThroughRank(0);
        if (atZero != 0) {
            throw fail(def, "perk '" + perk.id() + "' costThroughRank(0) must be 0 (nothing spent before rank 1), was " + atZero);
        }
        int prev = 0;
        for (int rank = 1; rank <= perk.maxRank(); rank++) {
            int cumulative = perk.costThroughRank(rank);
            if (cumulative < prev) {
                throw fail(def, "perk '" + perk.id() + "' has a decreasing cost curve: costThroughRank(" + rank + ")="
                        + cumulative + " < costThroughRank(" + (rank - 1) + ")=" + prev
                        + " (a cumulative cost must never decrease, or a rank-up would refund points)");
            }
            prev = cumulative;
        }
    }

    private static void validateEffects(SkillDefinition def, SkillPerk perk, Map<Identifier, SkillAbility> abilities,
                                        Map<String, SkillCapability<?>> capabilityIds) {
        int maxRank = perk.maxRank();   // guaranteed >= 1 by the structural guards above
        for (int rank = 1; rank <= maxRank; rank++) {
            Set<Identifier> attributesThisRank = new HashSet<>();
            for (PerkEffect effect : perk.effectsAtRank(rank)) {
                switch (effect) {
                    case AttributeEffect a -> {
                        if (STAT_ATTRIBUTES.contains(a.attribute())) {
                            throw fail(def, "perk '" + perk.id() + "' targets stat attribute '" + a.attribute()
                                    + "'; skills must not feed the stat layer");
                        }
                        if (!attributesThisRank.add(a.attribute())) {
                            throw fail(def, "perk '" + perk.id() + "' has two attribute effects on '" + a.attribute()
                                    + "' at rank " + rank + "; a perk writes at most one modifier per attribute"
                                    + " (the stable id is rank- and operation-independent)");
                        }
                    }
                    case AbilityUnlock u -> {
                        if (!abilities.containsKey(u.abilityId())) {
                            throw fail(def, "perk '" + perk.id() + "' unlocks unregistered ability '" + u.abilityId() + "'");
                        }
                    }
                    case CapabilityGrant<?> g -> {
                        checkCapabilityFold(def, perk, g);
                        checkCapabilityId(def, capabilityIds, g.capability());
                    }
                }
            }
        }
    }

    private static <T> void checkCapabilityFold(SkillDefinition def, SkillPerk perk, CapabilityGrant<T> grant) {
        try {
            grant.capability().combine().apply(grant.capability().absent(), grant.value());
        } catch (RuntimeException e) {
            throw fail(def, "perk '" + perk.id() + "' grants capability '" + grant.capability().id()
                    + "' with a value its own combine rejects", e);
        }
    }

    private static void checkCapabilityId(SkillDefinition def, Map<String, SkillCapability<?>> seen,
                                          SkillCapability<?> capability) {
        SkillCapability<?> prev = seen.putIfAbsent(capability.id(), capability);
        if (prev != null && prev != capability) {
            throw fail(def, "capability id '" + capability.id() + "' is declared by two different SkillCapability"
                    + " constants; declare each capability once as a shared constant");
        }
    }

    private static void checkNoCycle(SkillDefinition def, Map<String, SkillPerk> byId) {
        Set<String> onPath = new HashSet<>();
        Set<String> done = new HashSet<>();
        for (String id : byId.keySet()) {
            if (hasCycle(id, byId, onPath, done)) {
                throw fail(def, "prerequisite cycle involving perk '" + id + "'");
            }
        }
    }

    private static boolean hasCycle(String id, Map<String, SkillPerk> byId, Set<String> onPath, Set<String> done) {
        if (done.contains(id)) {
            return false;
        }
        if (!onPath.add(id)) {
            return true; // id is already on the current DFS path -> cycle
        }
        for (String pre : byId.get(id).prerequisites()) {
            if (hasCycle(pre, byId, onPath, done)) {
                return true;
            }
        }
        onPath.remove(id);
        done.add(id);
        return false;
    }

    private static IllegalStateException fail(SkillDefinition def, String detail) {
        return new IllegalStateException("Invalid skill '" + def.id() + "': " + detail);
    }

    private static IllegalStateException fail(SkillDefinition def, String detail, Throwable cause) {
        return new IllegalStateException("Invalid skill '" + def.id() + "': " + detail, cause);
    }
}
