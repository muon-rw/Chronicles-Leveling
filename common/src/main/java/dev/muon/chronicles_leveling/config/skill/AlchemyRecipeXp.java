package dev.muon.chronicles_leveling.config.skill;

import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.util.Walkable;
import me.fzzyhmstrs.fzzy_config.validation.minecraft.ValidatedIdentifier;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import net.minecraft.resources.Identifier;

/**
 * One row mapping a brewing recipe (or any string id, since pack-side recipe
 * ids are stable) to a base XP value. Amplifier scaling is applied separately
 * via {@link AlchemySkillConfig#amplifierMultiplier}.
 *
 * <p>The id is stored as an {@link net.minecraft.resources.Identifier} so the
 * config GUI offers id validation, but at evaluation time we match on the
 * brewing recipe's registered id (vanilla recipes are namespaced under
 * {@code minecraft:} — e.g. {@code minecraft:strength}).
 */
public class AlchemyRecipeXp implements Walkable {

    @Comment("Brewing recipe id (e.g. minecraft:strength). The recipe registry holds these ids.")
    public ValidatedIdentifier recipe;

    @Comment("Base XP awarded when one potion item is produced via this recipe.")
    public ValidatedDouble baseXp;

    public AlchemyRecipeXp() {
        this(Identifier.fromNamespaceAndPath("minecraft", "awkward"), 5.0);
    }

    public AlchemyRecipeXp(Identifier recipe, double baseXp) {
        this.recipe = new ValidatedIdentifier(recipe);
        this.baseXp = new ValidatedDouble(baseXp, 100_000.0, 0.0);
    }
}
