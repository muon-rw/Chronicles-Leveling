package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.StatModifierSpec;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedAny;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-authoritative settings synced to clients.
 *
 * <p>Three groups:
 * <ul>
 *   <li><b>Curve</b> — XP-per-level math + how many stat points each level grants.</li>
 *   <li><b>Stat modifiers</b> — per-stat list of "this stat adds X to attribute Y per
 *       point spent". Edited live; writes go through {@link #getStatModifierSpecs(String)}.</li>
 *   <li><b>Display</b> — defaults DD won't override (fallback nameplate, etc.).</li>
 * </ul>
 *
 * <p>Defaults give every stat one safe vanilla mapping so a brand-new install
 * has visible feedback. Pack authors are expected to flesh them out — and to
 * remap them onto {@code Combat-Attributes} attributes once that mod is on the
 * classpath. Identifiers are stored as strings in the config and parsed at
 * read time so we don't have to register them at config-construction time
 * (when the attribute registry isn't built yet).
 */
public class ConfigSync extends Config {

    public ConfigSync() {
        super(Identifier.fromNamespaceAndPath(ChroniclesLeveling.MOD_ID, "sync"));
    }

    // --- Curve ---

    @Comment("Stat points granted on each level-up.")
    public ValidatedInt pointsPerLevel = new ValidatedInt(1, 100, 0);

    @Comment("Stat points the player has at level 1, before any leveling.")
    public ValidatedInt startingPoints = new ValidatedInt(0, 1000, 0);

    @Comment("XP cost to advance from level l to l+1. 'l' = current level. Examples: " +
            "'50 + 15 * (l - 1)^1.5' (default, playerex-ish), '100 * l' (linear), '50 * l^2' (quadratic).")
    public ValidatedExpression xpCurveExpression =
            new ValidatedExpression("50 + 15 * (l - 1)^1.5", Set.of('l'));

    // --- Stat modifier mappings ---
    // One section per stat; FzzyConfig generates a tidy "Stat Modifiers > Strength" tree in the GUI.

    public StatModifierList strength     = StatModifierList.defaultFor(ModStats.STRENGTH);
    public StatModifierList dexterity    = StatModifierList.defaultFor(ModStats.DEXTERITY);
    public StatModifierList constitution = StatModifierList.defaultFor(ModStats.CONSTITUTION);
    public StatModifierList intelligence = StatModifierList.defaultFor(ModStats.INTELLIGENCE);
    public StatModifierList wisdom       = StatModifierList.defaultFor(ModStats.WISDOM);
    public StatModifierList luckiness    = StatModifierList.defaultFor(ModStats.LUCKINESS);

    // --- Skill XP curves ---
    // One ValidatedExpression per skill, grouped under a section so FzzyConfig renders
    // a "Skill Curves" subtree. 'l' = current skill level.

    public SkillCurves skillCurves = new SkillCurves();

    // --- Display ---

    @Comment("Draw the player level above the player's nameplate. If Dynamic-Difficulty is also loaded and configured to render player levels, you'll see two — disable DD's injectLevelIntoPlayers to deduplicate.")
    public ValidatedBoolean injectLevelIntoOwnNameplate = new ValidatedBoolean(true);

    /** Lookup helper so the applier doesn't need to know each field's name. */
    public List<StatModifierSpec> getStatModifierSpecs(String statId) {
        return switch (statId) {
            case ModStats.STRENGTH     -> strength.specs;
            case ModStats.DEXTERITY    -> dexterity.specs;
            case ModStats.CONSTITUTION -> constitution.specs;
            case ModStats.INTELLIGENCE -> intelligence.specs;
            case ModStats.WISDOM       -> wisdom.specs;
            case ModStats.LUCKINESS    -> luckiness.specs;
            default -> List.of();
        };
    }

    /**
     * Per-skill XP-to-next-level expressions. {@code l} is bound to the
     * current skill level when evaluated. The default formula is the same
     * across all skills so a fresh install has consistent pacing — pack
     * authors are expected to retune individual skills as they design the
     * trainers that grant XP into them.
     */
    public static class SkillCurves extends ConfigSection {

        private static final String DEFAULT_FORMULA = "100 + 25 * (l - 1)^1.5";

        @Comment("XP required to advance from level l to l+1 in Weaponry.")
        public ValidatedExpression weaponry   = curve();
        @Comment("XP required to advance from level l to l+1 in Archery.")
        public ValidatedExpression archery    = curve();
        @Comment("XP required to advance from level l to l+1 in Magic.")
        public ValidatedExpression magic      = curve();
        @Comment("XP required to advance from level l to l+1 in Armor.")
        public ValidatedExpression armor      = curve();
        @Comment("XP required to advance from level l to l+1 in Acrobatics.")
        public ValidatedExpression acrobatics = curve();
        @Comment("XP required to advance from level l to l+1 in Alchemy.")
        public ValidatedExpression alchemy    = curve();
        @Comment("XP required to advance from level l to l+1 in Mining.")
        public ValidatedExpression mining     = curve();
        @Comment("XP required to advance from level l to l+1 in Speech.")
        public ValidatedExpression speech     = curve();
        @Comment("XP required to advance from level l to l+1 in Farming.")
        public ValidatedExpression farming    = curve();
        @Comment("XP required to advance from level l to l+1 in Enchanting.")
        public ValidatedExpression enchanting = curve();
        @Comment("XP required to advance from level l to l+1 in Smithing.")
        public ValidatedExpression smithing   = curve();
        @Comment("XP required to advance from level l to l+1 in Fishing.")
        public ValidatedExpression fishing    = curve();

        private static ValidatedExpression curve() {
            return new ValidatedExpression(DEFAULT_FORMULA, Set.of('l'));
        }

        public ValidatedExpression curveFor(String skillId) {
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
    }

    /**
     * Wraps a list of {@link StatModifierSpec} in a section so each stat gets
     * its own collapsible heading in the FzzyConfig GUI. Couldn't use a bare
     * list here without losing that grouping.
     */
    public static class StatModifierList extends ConfigSection {

        @Comment("Modifiers granted to other attributes per point spent in this stat.")
        public ValidatedList<StatModifierSpec> specs =
                new ValidatedAny<>(new StatModifierSpec()).toList(List.of());

        public StatModifierList() {
            // FzzyConfig deserialization ctor.
        }

        public StatModifierList(List<StatModifierSpec> initial) {
            this.specs = new ValidatedAny<>(new StatModifierSpec()).toList(initial);
        }

        /**
         * Sensible-default mapping per stat onto vanilla attributes, so a fresh
         * install has visible feedback before pack authors customize anything.
         * Pack authors will typically delete most of these once {@code Combat-Attributes}
         * is on the classpath and remap them to crit-chance / lifesteal / etc.
         */
        public static StatModifierList defaultFor(String statId) {
            Map<String, List<StatModifierSpec>> defaults = Map.of(
                    ModStats.STRENGTH, List.of(
                            new StatModifierSpec(idOf("attack_damage"), 0.5, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.DEXTERITY, List.of(
                            new StatModifierSpec(idOf("attack_speed"), 0.02, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(idOf("movement_speed"), 0.005, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                    ),
                    ModStats.CONSTITUTION, List.of(
                            new StatModifierSpec(idOf("max_health"), 1.0, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(idOf("armor_toughness"), 0.25, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.INTELLIGENCE, List.of(
                            // Placeholder until Combat-Attributes' magic_damage_bonus exists.
                            new StatModifierSpec(idOf("attack_damage"), 0.0, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.WISDOM, List.of(
                            // Placeholder for future "spell cooldown reduction" / "mana regen".
                            new StatModifierSpec(idOf("attack_damage"), 0.0, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.LUCKINESS, List.of(
                            new StatModifierSpec(idOf("luck"), 0.1, AttributeModifier.Operation.ADD_VALUE)
                    )
            );
            return new StatModifierList(defaults.getOrDefault(statId, List.of(new StatModifierSpec())));
        }

        private static Identifier idOf(String path) {
            return Identifier.fromNamespaceAndPath("minecraft", "generic." + path);
        }
    }
}
