package dev.muon.chronicles_leveling.skill.perk;

/**
 * What a perk contributes when unlocked. Pure data; an effect never mutates the
 * world. {@code SkillEffects.derive} pattern-matches this sealed set once and owns all
 * materialization (attribute modifiers, the capability cache, the granted-ability
 * set), exactly as {@code StatModifierApplier} owns attribute writes.
 *
 * <p>Adding a new SURFACE here is rare and deliberate: one permit plus one switch arm
 * the compiler points you at. New BEHAVIOR is the common case and never touches this
 * file: it is a new {@link SkillCapability} key or a new {@code SkillAbility}.
 */
public sealed interface PerkEffect permits AttributeEffect, CapabilityGrant, AbilityUnlock {
}
