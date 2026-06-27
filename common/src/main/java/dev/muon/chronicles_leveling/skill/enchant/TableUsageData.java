package dev.muon.chronicles_leveling.skill.enchant;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Persistent per-player record of how many times the player has enchanted at each table; the backing data for
 * Wizard's Study's "most-used table". Immutable; mutators return a new instance. Keyed by {@link GlobalPos} so
 * tables in different dimensions stay distinct.
 */
public record TableUsageData(Map<GlobalPos, Integer> counts) {

    public static final TableUsageData DEFAULT = new TableUsageData(Map.of());

    public static final Codec<TableUsageData> CODEC =
            Codec.unboundedMap(GlobalPos.CODEC, Codec.INT).xmap(TableUsageData::new, TableUsageData::counts);

    public TableUsageData {
        counts = Map.copyOf(counts);
    }

    /** A copy with the given table's use count incremented by one. */
    public TableUsageData recordUse(GlobalPos table) {
        Map<GlobalPos, Integer> next = new HashMap<>(counts);
        next.merge(table, 1, Integer::sum);
        return new TableUsageData(next);
    }

    /**
     * Drops every recorded table the predicate rejects (a destroyed table, so {@link #mostUsed} falls back to the
     * next-highest survivor instead of pointing at empty air). Returns {@code this} unchanged when all are kept, so
     * callers can skip a needless write.
     */
    public TableUsageData pruned(Predicate<GlobalPos> keep) {
        Map<GlobalPos, Integer> next = null;
        for (GlobalPos table : counts.keySet()) {
            if (!keep.test(table)) {
                if (next == null) {
                    next = new HashMap<>(counts);
                }
                next.remove(table);
            }
        }
        return next == null ? this : new TableUsageData(next);
    }

    /** The table the player has enchanted at most, or {@code null} if none is recorded. */
    public GlobalPos mostUsed() {
        GlobalPos best = null;
        int bestCount = 0;
        for (Map.Entry<GlobalPos, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }
}
