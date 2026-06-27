package dev.muon.chronicles_leveling.skill.perk;

import net.minecraft.resources.Identifier;

/**
 * Marks an active ability as unlocked while the perk is held. A pure id reference;
 * the ability implementation lives in the skill's ability list and is resolved
 * through {@code SkillRegistry} by the cast pipeline. A perk carrying this renders
 * with an ability badge rather than as a separate tree node.
 */
public record AbilityUnlock(Identifier abilityId) implements PerkEffect {
}
