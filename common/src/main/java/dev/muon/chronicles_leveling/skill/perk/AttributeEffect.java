package dev.muon.chronicles_leveling.skill.perk;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Adds a stable-id {@link AttributeModifier} to an attribute while the perk is held.
 *
 * <p>Always UNCONDITIONAL: a static modifier cannot be "only while sprinting", so
 * contextual combat bonuses are {@link CapabilityGrant}s the proc site reads in
 * context. That makes the conditional-attribute footgun unrepresentable rather than
 * something to validate against.
 *
 * <p>{@code magnitude} is a level-aware function, not a raw double, so passive scaling
 * and its cap live in one place. Perk rank multiplies the evaluated amount in the
 * applier; the magnitude never sees rank, keeping each effect single-purpose. So for a
 * multi-rank perk the {@code Magnitude} must stay rank-CONSTANT; baking rank into it
 * (e.g. {@code perLevel(0.1 * rank, ...)} inside {@code effectsAtRank}) double-scales.
 * Only a {@link CapabilityGrant}'s value should vary with rank.
 */
public record AttributeEffect(Identifier attribute, AttributeModifier.Operation operation, Magnitude magnitude)
        implements PerkEffect {
}
