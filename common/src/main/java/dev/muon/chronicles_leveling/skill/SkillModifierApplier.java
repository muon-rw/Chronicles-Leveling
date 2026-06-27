package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.perk.AttributeEffect;
import dev.muon.chronicles_leveling.skill.perk.PerkEffect;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Materializes a player's unlocked skill perks onto vanilla {@link AttributeInstance}s;
 * the skill-keyed twin of {@code StatModifierApplier}, under the stable-id namespace
 * {@code chronicles_leveling:skill/<skill>/<perk>/<attr>}.
 *
 * <p>One critical difference from the stat applier: the removal authority is the FULL
 * perk registry, not the player's unlocked set. The unlocked set shrinks on respec, so
 * a removed perk's modifier id would otherwise be unreachable and leak as a ghost
 * modifier. Clearing every registered perk's potential attributes on each recompute
 * guarantees a clean slate before re-applying the held perks.
 *
 * <p>The stable id is rank-INDEPENDENT: rank scales the amount, never the id, so a
 * rank-down is a clean removeModifier + addPermanentModifier with no orphan.
 */
public final class SkillModifierApplier {

    private SkillModifierApplier() {}

    /**
     * Re-derives the player's skill effects, clears every skill modifier this layer
     * could have written, re-applies the ones the player currently holds, and refreshes
     * the capability/ability cache. Call after login, respawn, clone, a skill level-up,
     * and any perk spend/respec.
     */
    public static void recompute(ServerPlayer player) {
        SkillEffects.Derived derived = SkillEffects.derive(player);
        clearAll(player);
        for (SkillEffects.AttributeWrite write : derived.writes()) {
            apply(player, write);
        }
        // Attribute writes are eager (above); the capability/ability cache is lazy, so
        // invalidate it so the next handler read re-derives from current perk ranks.
        SkillEffects.markDirty(player);
    }

    /** Removes every modifier this layer could have written, across the whole registry. */
    private static void clearAll(ServerPlayer player) {
        for (SkillDefinition def : SkillRegistry.all()) {
            for (SkillPerk perk : def.perks()) {
                for (Identifier attribute : potentialAttributes(perk)) {
                    AttributeInstance instance = instanceFor(player, attribute);
                    if (instance != null) {
                        instance.removeModifier(SkillEffects.modifierId(def.id(), perk.id(), attribute));
                    }
                }
            }
        }
    }

    private static void apply(ServerPlayer player, SkillEffects.AttributeWrite write) {
        AttributeInstance instance = instanceFor(player, write.attribute());
        if (instance == null) {
            ChroniclesLeveling.LOG.debug("Skill modifier targets unknown/absent attribute '{}', skipping", write.attribute());
            return;
        }
        instance.removeModifier(write.modifierId());
        if (write.amount() == 0.0) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(write.modifierId(), write.amount(), write.operation()));
    }

    /** Every attribute any rank of the perk could target; the removal surface. */
    private static Set<Identifier> potentialAttributes(SkillPerk perk) {
        Set<Identifier> attributes = new HashSet<>();
        int maxRank = perk.maxRank();   // freeze guarantees >= 1
        for (int rank = 1; rank <= maxRank; rank++) {
            for (PerkEffect effect : perk.effectsAtRank(rank)) {
                if (effect instanceof AttributeEffect a) {
                    attributes.add(a.attribute());
                }
            }
        }
        return attributes;
    }

    private static AttributeInstance instanceFor(ServerPlayer player, Identifier attributeId) {
        Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(
                ResourceKey.create(Registries.ATTRIBUTE, attributeId));
        if (holder.isEmpty()) {
            return null;
        }
        return player.getAttribute(holder.get());
    }
}
