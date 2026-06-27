package dev.muon.chronicles_leveling.skill.perk;

/**
 * Grants a typed {@link SkillCapability} value while the perk is held. The applier
 * folds every granting perk's value through the capability's own combine rule into a
 * per-player cache that handlers query (auto-replant, double-drop chance, a
 * bleed-on-hit descriptor, ...).
 */
public record CapabilityGrant<T>(SkillCapability<T> capability, T value) implements PerkEffect {
}
