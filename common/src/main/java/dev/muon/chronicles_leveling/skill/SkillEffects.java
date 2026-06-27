package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.perk.AbilityUnlock;
import dev.muon.chronicles_leveling.skill.perk.AttributeEffect;
import dev.muon.chronicles_leveling.skill.perk.CapabilityGrant;
import dev.muon.chronicles_leveling.skill.perk.PerkEffect;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The one perk-walk that turns a player's unlocked perks into materialized state, plus
 * a transient per-player cache of the parts that handlers read every tick.
 *
 * <p>{@link #derive(Player)} is pure and side-effect-free; it never touches an
 * {@code AttributeInstance}, so it is safe to run on the client (for the HUD) as well
 * as the server. It is a pure function of the player's synced {@code perkRanks} and the
 * frozen registry, which is why the cache can never desync: there is no second stored
 * or synced representation.
 *
 * <p>{@code SkillModifierApplier} consumes the {@link Derived#writes} to materialize
 * attributes and then caches the result; server handlers read capabilities and
 * granted abilities via {@link #get}/{@link #has}/{@link #hasAbility}, which lazily
 * derive on a cache miss.
 */
public final class SkillEffects {

    private SkillEffects() {}

    /** One intended attribute modifier: a stable id, its target, and the resolved amount. */
    public record AttributeWrite(Identifier modifierId, Identifier attribute, double amount,
                                 AttributeModifier.Operation operation) {}

    /** The full result of a perk-walk: capability values, granted ability ids, and attribute writes. */
    public record Derived(Map<SkillCapability<?>, Object> capabilities, Set<Identifier> abilities,
                          List<AttributeWrite> writes) {}

    /** Server-thread-only transient cache; rebuilt from synced data, never persisted or synced. */
    private static final Map<UUID, Derived> CACHE = new HashMap<>();

    /** Stable id for the (skill, perk, attribute) modifier this layer writes. Rank-independent. */
    public static Identifier modifierId(String skillId, String perkId, Identifier attribute) {
        return ChroniclesLeveling.id(
                "skill/" + skillId + "/" + perkId + "/" + attribute.getNamespace() + "/" + attribute.getPath());
    }

    /**
     * Walks every unlocked perk and produces the materialized state. Pure: reads the
     * (synced) skill data + frozen registry, writes nothing. The same function backs the
     * server cache and the client HUD.
     */
    public static Derived derive(Player player) {
        Map<SkillCapability<?>, Object> capabilities = new HashMap<>();
        Set<Identifier> abilities = new HashSet<>();
        List<AttributeWrite> writes = new ArrayList<>();

        PlayerSkillData data = PlayerSkillManager.get(player);
        for (SkillDefinition def : SkillRegistry.all()) {
            PlayerSkillData.SkillEntry entry = data.get(def.id());
            int level = entry.level();
            for (SkillPerk perk : def.perks()) {
                int rank = entry.rankOf(perk.id());
                if (rank <= 0) {
                    continue;
                }
                for (PerkEffect effect : perk.effectsAtRank(rank)) {
                    switch (effect) {
                        case AttributeEffect a -> writes.add(new AttributeWrite(
                                modifierId(def.id(), perk.id(), a.attribute()),
                                a.attribute(),
                                a.magnitude().eval(level) * rank,
                                a.operation()));
                        case CapabilityGrant<?> g -> fold(capabilities, g);
                        case AbilityUnlock u -> abilities.add(u.abilityId());
                    }
                }
            }
        }
        return new Derived(capabilities, abilities, writes);
    }

    private static <T> void fold(Map<SkillCapability<?>, Object> capabilities, CapabilityGrant<T> grant) {
        SkillCapability<T> capability = grant.capability();
        @SuppressWarnings("unchecked")
        T current = (T) capabilities.getOrDefault(capability, capability.absent());
        capabilities.put(capability, capability.combine().apply(current, grant.value()));
    }

    // --- server-side cache ---

    /**
     * Invalidates the snapshot so the next read re-derives. This is the single
     * invalidation signal: the applier calls it after a recompute, and any future
     * out-of-band perk-state change calls it too, so "cache == f(perkRanks)" holds for
     * every mutator with no eager-fill tail to forget.
     */
    public static void markDirty(ServerPlayer player) {
        CACHE.remove(player.getUUID());
    }

    /** Drops the snapshot on logout. */
    public static void clear(UUID playerId) {
        CACHE.remove(playerId);
    }

    /** The current value of a capability for the player, or its {@code absent} value. */
    public static <T> T get(ServerPlayer player, SkillCapability<T> capability) {
        @SuppressWarnings("unchecked")
        T value = (T) snapshot(player).capabilities().getOrDefault(capability, capability.absent());
        return value;
    }

    /** Capability value for any player: the cached server snapshot, or a fresh client-safe {@link #derive} otherwise. */
    public static <T> T capabilityValue(Player player, SkillCapability<T> capability) {
        if (player instanceof ServerPlayer server) {
            return get(server, capability);
        }
        @SuppressWarnings("unchecked")
        T value = (T) derive(player).capabilities().getOrDefault(capability, capability.absent());
        return value;
    }

    /** Whether the player has any non-absent value for the capability (true flag, non-zero chance, ...). */
    public static <T> boolean has(ServerPlayer player, SkillCapability<T> capability) {
        return !Objects.equals(get(player, capability), capability.absent());
    }

    /** Whether the player has unlocked the given active ability. */
    public static boolean hasAbility(ServerPlayer player, Identifier abilityId) {
        return snapshot(player).abilities().contains(abilityId);
    }

    private static Derived snapshot(ServerPlayer player) {
        return CACHE.computeIfAbsent(player.getUUID(), id -> derive(player));
    }
}
