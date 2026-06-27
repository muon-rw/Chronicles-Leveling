package dev.muon.chronicles_leveling.skill.perk;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Static factories for {@link PerkEffect}s, meant to be {@code import static}'d into
 * skill-definition classes so a tree reads as its design rather than its boilerplate:
 * {@code attr(...)}, {@code grant(...)}, {@code unlocks(...)}. The scaling shapes come
 * from {@link Magnitude#flat} / {@link Magnitude#perLevel}.
 */
public final class Perks {

    private Perks() {}

    public static AttributeEffect attr(Identifier attribute, AttributeModifier.Operation operation, Magnitude magnitude) {
        return new AttributeEffect(attribute, operation, magnitude);
    }

    public static <T> CapabilityGrant<T> grant(SkillCapability<T> capability, T value) {
        return new CapabilityGrant<>(capability, value);
    }

    public static AbilityUnlock unlocks(Identifier abilityId) {
        return new AbilityUnlock(abilityId);
    }
}
