package dev.muon.chronicles_leveling.skill.ability;

/**
 * Why a cast was denied, sent to the client so it can render the message (text/styling client-side) and play a
 * local reaction. {@link #UNAVAILABLE} carries an ability-supplied message component; the rest the client builds
 * from the reason (and {@code detail}, e.g. cooldown seconds).
 */
public enum CastDenyReason {

    COOLDOWN("chronicles_leveling.ability.error.cooldown"),
    NEED_STAMINA("chronicles_leveling.ability.error.stamina"),
    NEED_MANA("chronicles_leveling.ability.error.mana"),
    UNAVAILABLE("chronicles_leveling.ability.error.unavailable"),
    INTERRUPTED("chronicles_leveling.ability.error.interrupted");

    private final String translationKey;

    CastDenyReason(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    /** Whether the client substitutes {@code detail} into the message (only COOLDOWN, which shows the seconds left). */
    public boolean usesDetail() {
        return this == COOLDOWN;
    }
}
