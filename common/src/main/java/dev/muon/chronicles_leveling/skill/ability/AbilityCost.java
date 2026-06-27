package dev.muon.chronicles_leveling.skill.ability;

/**
 * The Combat-Attributes resource cost to activate an ability. A non-positive
 * component is "free"; the resource seam treats {@code <= 0} as trivially affordable.
 * v1 abilities use a single pool; both components may be set if a future ability draws
 * on stamina and mana together.
 */
public record AbilityCost(float stamina, float mana) {

    public static final AbilityCost NONE = new AbilityCost(0f, 0f);

    public static AbilityCost stamina(float amount) {
        return new AbilityCost(amount, 0f);
    }

    public static AbilityCost mana(float amount) {
        return new AbilityCost(0f, amount);
    }

    public static AbilityCost none() {
        return NONE;
    }
}
