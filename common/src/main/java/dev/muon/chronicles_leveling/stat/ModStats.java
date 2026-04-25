package dev.muon.chronicles_leveling.stat;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The six baseline stat attributes ({@code STR / DEX / CON / INT / WIS / LUCK}).
 *
 * <p>Pattern lifted from {@code Combat-Attributes}' {@code ModAttributes}:
 * a single {@link #ALL} list is the source of truth, and each loader's
 * registration code (Fabric / NeoForge) drives off it. Holders are populated
 * back into {@link #HOLDERS} once registration completes so common code can
 * look them up by id.
 *
 * <p>These are deliberately {@code RangedAttribute}s (not the diminishing
 * variant). The values are clamped to a small integer range and are presented
 * as integers everywhere — the intent is "skill points" semantics, not a
 * percent or curve.
 */
public final class ModStats {

    private ModStats() {}

    /** Stable id used in registry paths, lang keys, and config keys. */
    public record Entry(String id, double defaultValue, double minValue, double maxValue) {}

    public static final String STRENGTH = "strength";
    public static final String DEXTERITY = "dexterity";
    public static final String CONSTITUTION = "constitution";
    public static final String INTELLIGENCE = "intelligence";
    public static final String WISDOM = "wisdom";
    public static final String LUCKINESS = "luckiness";

    /**
     * Iteration order here is the order stats render in the level-up screen.
     * Default min/max chosen to be wide enough that {@code /attribute} commands
     * and admin-driven respec stays unconstrained; the screen enforces its own
     * "you must have unspent points to allocate" rule.
     */
    public static final List<Entry> ALL = List.of(
            new Entry(STRENGTH,     0.0, 0.0, 999.0),
            new Entry(DEXTERITY,    0.0, 0.0, 999.0),
            new Entry(CONSTITUTION, 0.0, 0.0, 999.0),
            new Entry(INTELLIGENCE, 0.0, 0.0, 999.0),
            new Entry(WISDOM,       0.0, 0.0, 999.0),
            new Entry(LUCKINESS,    0.0, 0.0, 999.0)
    );

    private static final Map<String, Holder<Attribute>> HOLDERS = new HashMap<>();

    public static void put(String id, Holder<Attribute> holder) {
        HOLDERS.put(id, holder);
    }

    public static Holder<Attribute> get(String id) {
        Holder<Attribute> h = HOLDERS.get(id);
        if (h == null) throw new IllegalStateException("Stat attribute '" + id + "' not registered");
        return h;
    }

    public static boolean isRegistered(String id) {
        return HOLDERS.containsKey(id);
    }
}
