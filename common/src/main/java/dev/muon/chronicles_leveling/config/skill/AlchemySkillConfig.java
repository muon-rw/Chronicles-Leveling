package dev.muon.chronicles_leveling.config.skill;

import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedAny;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;

import java.util.List;
import java.util.Set;

/**
 * Alchemy skill config. XP is granted only when a potion is actually produced
 * by a brewing stand the player is standing at; the routing layer keys the
 * award off the brewing recipe id rather than the output potion's effect, so
 * pack-defined recipes that produce vanilla potions still get distinct rates.
 *
 * <p>{@link #recipes} is a lookup table of explicit XP overrides; anything
 * outside that table falls back to {@link #defaultBaseXp}. Amplifier scaling
 * is one global expression — most packs want a single curve here, and a
 * per-recipe override would just duplicate the recipe's base XP into a
 * second knob.
 */
public class AlchemySkillConfig extends SkillConfig {

    @Comment("Base XP per potion produced by a recipe not listed below. Set to 0 to disable XP for unlisted recipes.")
    public ValidatedDouble defaultBaseXp = new ValidatedDouble(5.0, 100_000.0, 0.0);

    @Comment("Multiplier applied to base XP based on the output potion's amplifier. 'a' = amplifier (0 = level I, 1 = level II, ...). Default doubles XP per amplifier tier.")
    public ValidatedExpression amplifierMultiplier = new ValidatedExpression("1 + a", Set.of('a'));

    @Comment("Per-recipe XP overrides. Recipe id is what the brewing recipe is registered as (e.g. minecraft:strength).")
    public ValidatedList<AlchemyRecipeXp> recipes =
            new ValidatedAny<>(new AlchemyRecipeXp()).toList(List.of());

    public AlchemySkillConfig() {
        super(Skills.ALCHEMY);
    }
}
