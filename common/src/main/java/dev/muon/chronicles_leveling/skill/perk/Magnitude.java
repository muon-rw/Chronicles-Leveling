package dev.muon.chronicles_leveling.skill.perk;

/**
 * Level → bonus amount, capped. The shape is authored in code; config supplies the
 * numeric tuning per perk id later. A tiny sealed family the applier evaluates; the
 * records stay inert (and therefore trivially serializable if a data door is ever
 * wanted).
 */
public sealed interface Magnitude permits Magnitude.Flat, Magnitude.PerLevel {

    /** The bonus amount at the given skill level (before any rank multiplier). */
    double eval(int skillLevel);

    record Flat(double value) implements Magnitude {
        @Override public double eval(int skillLevel) {
            return value;
        }
    }

    /**
     * {@code perLevel} per skill level, clamped to {@code cap}. The crit-chance catalog case.
     *
     * <p>{@code cap} bounds the PER-RANK amount: the applier multiplies the evaluated
     * result by the perk's rank, so a {@code maxRank > 1} node can reach {@code cap * maxRank}.
     * The hard ceiling for an attribute (e.g. 50% crit) is enforced on the attribute's own
     * {@code RangedAttribute} max, not here.
     */
    record PerLevel(double perLevel, double cap) implements Magnitude {
        @Override public double eval(int skillLevel) {
            return Math.min(perLevel * skillLevel, cap);
        }
    }

    static Magnitude flat(double value) {
        return new Flat(value);
    }

    static Magnitude perLevel(double perLevel, double cap) {
        return new PerLevel(perLevel, cap);
    }
}
