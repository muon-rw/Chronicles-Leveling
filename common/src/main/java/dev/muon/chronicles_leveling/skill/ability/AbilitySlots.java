package dev.muon.chronicles_leveling.skill.ability;

/**
 * Action-bar ability-slot constants, shared by the server (slot-bind validation), the client keybinds,
 * the HUD strip, and the binding UI. A sparse {@code Map<Integer,String>} on {@code PlayerSkillData}
 * holds the bindings, so this count can grow without a save/wire shape change.
 */
public final class AbilitySlots {

    private AbilitySlots() {}

    public static final int COUNT = 4;

    public static boolean isValid(int slot) {
        return slot >= 0 && slot < COUNT;
    }
}
