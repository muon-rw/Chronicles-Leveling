package dev.muon.chronicles_leveling.skill;

/**
 * An addon hook for adding or extending skills. Implementations call
 * {@link SkillRegistry#register} (a brand-new skill) or {@link SkillRegistry#contribute}
 * (perks/abilities onto an existing skill) inside {@link #contribute()}. They are
 * collected by the loader and run after the core skills register but before the
 * registry freezes, so a contribution is validated by the same fail-fast freeze pass.
 *
 * <p>Fabric: declared as a {@code chronicles_leveling:skills} entrypoint. NeoForge:
 * registered via {@code RegisterSkillContributionsEvent} on the mod bus.
 */
@FunctionalInterface
public interface SkillContributor {
    void contribute();
}
