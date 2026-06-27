package dev.muon.chronicles_leveling.skill.perk;

import java.util.function.BinaryOperator;

/**
 * A typed engine-effect key that server handlers query (e.g. {@code "auto_replant"},
 * {@code "double_drop_chance"}, {@code "bleed_on_hit"}). Open by design: core and
 * addons declare their own keys as static constants with no central registration;
 * there is no enum to extend and no second mechanism for addon vs. core.
 *
 * <p>The constant's identity is the key. Because {@link #combine} is a lambda (no value
 * equality), two separately-constructed capabilities with the same {@code id} are
 * DISTINCT cache keys and will NOT fold together, so declare each capability exactly
 * once as a shared {@code public static final} constant and reference that. Registry
 * freeze fails fast if two different constants are ever found sharing an id. The applier
 * folds every granting perk's value through {@link #combine} into a per-player cache,
 * answering {@link #absent} when no perk grants it.
 *
 * @param <T>     value type: {@link Boolean} for a flag, {@link Double} for a
 *                chance/magnitude, or a small record for a parametric proc
 * @param id      stable string id, used for logging/debug
 * @param absent  the value when no perk grants this capability
 * @param combine associative fold of multiple granting perks (OR for flags, sum/max for doubles)
 */
public record SkillCapability<T>(String id, T absent, BinaryOperator<T> combine) {

    /** A boolean flag: absent = false, combine = logical OR. */
    public static SkillCapability<Boolean> flag(String id) {
        return new SkillCapability<>(id, Boolean.FALSE, Boolean::logicalOr);
    }

    /** A numeric chance/magnitude: absent = 0, combine = sum (clamp at the read site if needed). */
    public static SkillCapability<Double> additive(String id) {
        return new SkillCapability<>(id, 0.0, Double::sum);
    }

    /** A parametric capability with a custom value type and fold. */
    public static <T> SkillCapability<T> of(String id, T absent, BinaryOperator<T> combine) {
        return new SkillCapability<>(id, absent, combine);
    }
}
