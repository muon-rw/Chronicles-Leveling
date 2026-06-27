package dev.muon.chronicles_leveling.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Per-player skill state: every skill the player has progressed in (keyed by
 * skill id) plus a cross-skill ability-cooldown map.
 *
 * <p>Skills the player hasn't touched aren't stored; {@link #get(String)}
 * returns {@link SkillEntry#DEFAULT} (level 1, 0 xp) for those, which is the
 * correct starting state.
 *
 * <p>One record, one Codec (disk), one StreamCodec (sync), so the loader's
 * attachment system persists + syncs the whole picture as a unit. Fields beyond
 * {@link #skills} use {@code optionalFieldOf}, so saves written before they
 * existed round-trip cleanly.
 *
 * <ul>
 *   <li>{@link #skills}: per-skill level / xp / spent points / unlocked perk ranks.</li>
 *   <li>{@link #abilityCooldownEnds}: abilityId to game-time tick the cooldown ends.
 *       Cross-skill (an ability belongs to one skill, but cooldown is naturally a flat
 *       per-player map).</li>
 *   <li>{@link #abilitySlots}: action-bar slot index to bound ability id.</li>
 * </ul>
 */
public record PlayerSkillData(
        Map<String, SkillEntry> skills,
        Map<String, Long> abilityCooldownEnds,
        Map<Integer, String> abilitySlots) {

    public PlayerSkillData {
        skills = Map.copyOf(skills);
        abilityCooldownEnds = Map.copyOf(abilityCooldownEnds);
        abilitySlots = Map.copyOf(abilitySlots);
    }

    public static final PlayerSkillData DEFAULT = new PlayerSkillData(Map.of(), Map.of(), Map.of());

    /** Slot index as an NBT-safe string key (an int-keyed map can't encode to a compound tag). */
    private static final Codec<Integer> SLOT_KEY = Codec.STRING.flatXmap(
            s -> {
                try {
                    return DataResult.success(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Not an ability slot index: " + s);
                }
            },
            i -> DataResult.success(String.valueOf(i)));

    public static final Codec<PlayerSkillData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, SkillEntry.CODEC).fieldOf("skills").forGetter(PlayerSkillData::skills),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_cooldown_ends", Map.of())
                    .forGetter(PlayerSkillData::abilityCooldownEnds),
            Codec.unboundedMap(SLOT_KEY, Codec.STRING).optionalFieldOf("ability_slots", Map.of())
                    .forGetter(PlayerSkillData::abilitySlots)
    ).apply(instance, PlayerSkillData::new));

    public static final StreamCodec<ByteBuf, PlayerSkillData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, SkillEntry.STREAM_CODEC), PlayerSkillData::skills,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG), PlayerSkillData::abilityCooldownEnds,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.STRING_UTF8), PlayerSkillData::abilitySlots,
            PlayerSkillData::new
    );

    /** Returns the player's entry for the given skill, or {@link SkillEntry#DEFAULT} if untouched. */
    public SkillEntry get(String skillId) {
        return skills.getOrDefault(skillId, SkillEntry.DEFAULT);
    }

    /** Returns a new instance with the given skill replaced; the other fields are preserved. */
    public PlayerSkillData with(String skillId, SkillEntry entry) {
        Map<String, SkillEntry> updated = new HashMap<>(skills);
        updated.put(skillId, entry);
        return new PlayerSkillData(updated, abilityCooldownEnds, abilitySlots);
    }

    /** Game-time end-tick at which the ability's cooldown lapses, or 0 if it is not on cooldown. */
    public long abilityCooldownEnd(String abilityId) {
        return abilityCooldownEnds.getOrDefault(abilityId, 0L);
    }

    /**
     * Stamps a cooldown end-tick, pruning every entry already elapsed at {@code now} so the persisted +
     * synced map stays bounded to currently-cooling abilities (no unbounded growth).
     */
    public PlayerSkillData withAbilityCooldown(String abilityId, long endTick, long now) {
        Map<String, Long> updated = new HashMap<>();
        abilityCooldownEnds.forEach((id, end) -> {
            if (end > now) {
                updated.put(id, end);
            }
        });
        updated.put(abilityId, endTick);
        return new PlayerSkillData(skills, updated, abilitySlots);
    }

    /** The ability id bound to the given action-bar slot, or {@code null} if the slot is empty. */
    public String slotAbility(int slot) {
        return abilitySlots.get(slot);
    }

    /** The slot holding the given ability, or {@code -1} if it is unbound. */
    public int slotOf(String abilityId) {
        for (Map.Entry<Integer, String> e : abilitySlots.entrySet()) {
            if (e.getValue().equals(abilityId)) {
                return e.getKey();
            }
        }
        return -1;
    }

    /**
     * Binds {@code abilityId} to {@code slot} (a null/blank id clears the slot). An ability occupies at
     * most one slot, so it is first removed from any slot it already held.
     */
    public PlayerSkillData withAbilitySlot(int slot, String abilityId) {
        Map<Integer, String> updated = new HashMap<>(abilitySlots);
        if (abilityId != null) {
            updated.values().removeIf(abilityId::equals);
        }
        if (abilityId == null || abilityId.isBlank()) {
            updated.remove(slot);
        } else {
            updated.put(slot, abilityId);
        }
        return new PlayerSkillData(skills, abilityCooldownEnds, updated);
    }

    /**
     * Per-skill state.
     *
     * <ul>
     *   <li>{@link #level}: integer skill level, starts at 1.</li>
     *   <li>{@link #xp}: xp banked toward the next level.</li>
     *   <li>{@link #spentPoints}: points spent unlocking perks; recomputed from
     *       {@link #perkRanks} on every {@link #withPerkRank} write so it can never
     *       drift from the perks actually held.</li>
     *   <li>{@link #perkRanks}: perkId to current rank (a rank-1 perk is "unlocked";
     *       higher ranks are multi-rank nodes). Absent key = locked. This map is the
     *       source of truth for what the player has unlocked.</li>
     * </ul>
     *
     * <p>Earned points are derived ({@code level - 1}); only spent points are stored,
     * because the earned total is a pure function of {@link #level}.
     */
    public record SkillEntry(int level, int xp, int spentPoints, Map<String, Integer> perkRanks) {

        public static final SkillEntry DEFAULT = new SkillEntry(1, 0, 0, Map.of());

        public SkillEntry {
            perkRanks = Map.copyOf(perkRanks);
        }

        public static final Codec<SkillEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("level").forGetter(SkillEntry::level),
                Codec.INT.fieldOf("xp").forGetter(SkillEntry::xp),
                Codec.INT.optionalFieldOf("spent_points", 0).forGetter(SkillEntry::spentPoints),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("perk_ranks", Map.of())
                        .forGetter(SkillEntry::perkRanks)
        ).apply(instance, SkillEntry::new));

        public static final StreamCodec<ByteBuf, SkillEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SkillEntry::level,
                ByteBufCodecs.VAR_INT, SkillEntry::xp,
                ByteBufCodecs.VAR_INT, SkillEntry::spentPoints,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), SkillEntry::perkRanks,
                SkillEntry::new
        );

        /** Skill points earned so far: one per level above 1. */
        public int earnedPoints() {
            return Math.max(0, level - 1);
        }

        /**
         * The pool available to unlock perks: earned minus spent, with earned capped at the tree's total
         * cost. A tree never accrues more spendable SP than it can absorb, so a tree that completes at ~50 SP
         * stops showing new points past 50 even as the level keeps climbing.
         */
        public int availablePoints(int totalCost) {
            return Math.max(0, Math.min(earnedPoints(), Math.max(0, totalCost)) - spentPoints);
        }

        /** Current rank of a perk, or 0 if locked. */
        public int rankOf(String perkId) {
            return perkRanks.getOrDefault(perkId, 0);
        }

        public SkillEntry withLevel(int level) {
            return new SkillEntry(level, this.xp, this.spentPoints, this.perkRanks);
        }

        public SkillEntry withXp(int xp) {
            return new SkillEntry(this.level, xp, this.spentPoints, this.perkRanks);
        }

        /**
         * Sets a perk's rank ({@code rank <= 0} removes it) and recomputes
         * {@link #spentPoints} from the resulting map via {@code costThroughRank}
         * (perkId-agnostic: rank → <em>cumulative</em> point cost to reach that rank),
         * so the spent total can never drift from the perks actually held, even if a
         * perk's cost is retuned or a forged packet slips through.
         */
        public SkillEntry withPerkRank(String perkId, int rank, IntUnaryOperator costThroughRank) {
            Map<String, Integer> next = new HashMap<>(perkRanks);
            if (rank <= 0) {
                next.remove(perkId);
            } else {
                next.put(perkId, rank);
            }
            int spent = next.values().stream().mapToInt(costThroughRank::applyAsInt).sum();
            return new SkillEntry(this.level, this.xp, spent, next);
        }

        /** Clears every perk and the spent-point total, preserving level + xp. */
        public SkillEntry respec() {
            return new SkillEntry(this.level, this.xp, 0, Map.of());
        }
    }
}
