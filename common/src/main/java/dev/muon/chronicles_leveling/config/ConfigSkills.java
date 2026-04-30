package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.util.Walkable;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.minecraft.ValidatedIdentifier;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedAny;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

/**
 * Server-authoritative skill settings synced to clients. One file under
 * {@code config/chronicles_leveling/skills.toml} carries every per-skill knob
 * plus the cross-cutting XP-gain rules.
 *
 * <p>Layout:
 * <ul>
 *   <li><b>General</b> — XP blacklist + spawner multiplier shared by all skills.</li>
 *   <li><b>Per-skill sections</b> — one {@link ConfigSection} per skill in
 *       {@link Skills} order; subclass shape carries skill-specific fields,
 *       with {@link Skill#xpCurve} on the base.</li>
 * </ul>
 *
 * <p>Synced via {@link me.fzzyhmstrs.fzzy_config.api.RegisterType#BOTH} so server
 * curve edits show up in the client progress bar without a reconnect.
 */
public class ConfigSkills extends Config {

    public ConfigSkills() {
        super(Identifier.fromNamespaceAndPath(ChroniclesLeveling.MOD_ID, "skills"));
    }

    private static final String DEFAULT_CURVE = "100 + 25 * (l - 1)^1.5";

    // --- General ---

    @Comment("Entity ids that grant no skill XP when damaged or killed (e.g. minecraft:armor_stand). Format: 'namespace:path'.")
    public ValidatedList<String> entityXpBlacklist = new ValidatedString("").toList(List.of());

    @Comment("Multiplier applied to skill XP earned against entities spawned by a mob spawner. 1.0 for no modification; 0.0 disables farming spawners entirely.")
    public ValidatedDouble spawnerMobMultiplier = new ValidatedDouble(0.1, 1.0, 0.0);

    // --- Per-skill sections ---

    public Weaponry weaponry = new Weaponry();
    public Archery archery = new Archery();
    public Magic magic = new Magic();
    public Armor armor = new Armor();
    public Acrobatics acrobatics = new Acrobatics();
    public Alchemy alchemy = new Alchemy();

    public Skill mining = new Skill();
    public Skill speech = new Skill();
    public Skill farming = new Skill();
    public Skill enchanting = new Skill();
    public Skill smithing = new Skill();
    public Skill fishing = new Skill();

    /**
     * Returns the {@link Skill} section for a known skill id, or {@code null}
     * if the id isn't recognized. Use this for curve-only access; typed-field
     * callers should reach for the named field directly (e.g. {@code alchemy.recipes}).
     */
    public Skill section(String skillId) {
        return switch (skillId) {
            case Skills.WEAPONRY   -> weaponry;
            case Skills.ARCHERY    -> archery;
            case Skills.MAGIC      -> magic;
            case Skills.ARMOR      -> armor;
            case Skills.ACROBATICS -> acrobatics;
            case Skills.ALCHEMY    -> alchemy;
            case Skills.MINING     -> mining;
            case Skills.SPEECH     -> speech;
            case Skills.FARMING    -> farming;
            case Skills.ENCHANTING -> enchanting;
            case Skills.SMITHING   -> smithing;
            case Skills.FISHING    -> fishing;
            default -> null;
        };
    }

    // --- Section types ---

    /** Base shape: every skill has an XP curve. Subclasses bolt on gain-rule fields. */
    public static class Skill extends ConfigSection {
        @Comment("XP required to advance from level l to l+1. 'l' = current skill level.")
        public ValidatedExpression xpCurve = new ValidatedExpression(DEFAULT_CURVE, Set.of('l'));
    }

    /** XP from non-magic melee damage dealt. */
    public static class Weaponry extends Skill {
        @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
        public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));
    }

    /** XP from non-magic projectile damage dealt. */
    public static class Archery extends Skill {
        @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
        public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));
    }

    /** XP from magic damage dealt — anything tagged {@code #c:is_magic}. */
    public static class Magic extends Skill {
        @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
        public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));
    }

    /** XP from damage taken; pre-mitigation so heavy armor doesn't stall the skill. */
    public static class Armor extends Skill {
        @Comment("XP awarded per hit taken. 'd' = damage amount, pre-mitigation.")
        public ValidatedExpression xpPerDamageTaken = new ValidatedExpression("d", Set.of('d'));
    }

    /** XP from jumping (flat) and fall damage taken (formula). */
    public static class Acrobatics extends Skill {
        @Comment("XP awarded per jump. Tiny on purpose — pure idle activity.")
        public ValidatedDouble xpPerJump = new ValidatedDouble(0.05, 100.0, 0.0);

        @Comment("XP awarded per point of fall damage taken (pre-mitigation). 'd' = damage.")
        public ValidatedExpression xpPerFallDamage = new ValidatedExpression("d", Set.of('d'));
    }

    /**
     * XP awarded only when a brewing stand the player is standing at produces
     * a potion. {@link #recipes} is a lookup table of explicit overrides keyed
     * by the output potion's id; anything outside it falls back to
     * {@link #defaultBaseXp}.
     */
    public static class Alchemy extends Skill {
        @Comment("Base XP per potion produced by a recipe not listed below. Set to 0 to disable XP for unlisted recipes.")
        public ValidatedDouble defaultBaseXp = new ValidatedDouble(5.0, 100_000.0, 0.0);

        @Comment("Multiplier applied to base XP based on the output potion's amplifier. 'a' = amplifier (0 = level I, 1 = level II, ...). Default doubles XP per amplifier tier.")
        public ValidatedExpression amplifierMultiplier = new ValidatedExpression("1 + a", Set.of('a'));

        @Comment("Per-recipe XP overrides. Recipe id is what the brewing recipe is registered as (e.g. minecraft:strength).")
        public ValidatedList<RecipeXp> recipes = new ValidatedAny<>(new RecipeXp()).toList(List.of());
    }

    /**
     * One row mapping a brewing recipe id to a base XP value. Amplifier scaling
     * is applied separately via {@link Alchemy#amplifierMultiplier}.
     *
     * <p>Stored as an {@link Identifier} so the config GUI offers id validation;
     * vanilla recipes are namespaced under {@code minecraft:} (e.g.
     * {@code minecraft:strength}).
     */
    public static class RecipeXp implements Walkable {

        @Comment("Brewing recipe id (e.g. minecraft:strength). The recipe registry holds these ids.")
        public ValidatedIdentifier recipe;

        @Comment("Base XP awarded when one potion item is produced via this recipe.")
        public ValidatedDouble baseXp;

        public RecipeXp() {
            this(Identifier.fromNamespaceAndPath("minecraft", "awkward"), 5.0);
        }

        public RecipeXp(Identifier recipe, double baseXp) {
            this.recipe = new ValidatedIdentifier(recipe);
            this.baseXp = new ValidatedDouble(baseXp, 100_000.0, 0.0);
        }
    }
}
