package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.StatModifierSpec;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedAny;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
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
 * <p>Defaults give every stat one safe vanilla mapping so a brand-new installation
 * has visible feedback. Pack authors are expected to flesh them out — and to
 * remap them onto {@code Combat-Attributes} attributes once that mod is on the
 * classpath. Identifiers are stored as strings in the config and parsed at
 * read time so we don't have to register them at config-construction time
 * (when the attribute registry isn't built yet).errirewedieuejueddkdcdcdcc v vecdcfdfdfg c sd7ssdxujcdx   fvololdc
 * hi
 * fooooooood
 * - puppy
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

    @Comment("Hard cap on player level. 0 to disable restrictions.")
    public ValidatedInt maxLevel = new ValidatedInt(0, 10_000, 0);

    @Comment("Hard cap on points spent in any single stat. 0 to disable restrictions.")
    public ValidatedInt maxStatLevel = new ValidatedInt(0, 1_000, 0);

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

    // --- Skill XP gain ---

    @Comment("Entity ids that grant no skill XP when damaged or killed (e.g. minecraft:armor_stand). Format: 'namespace:path'.")
    public ValidatedList<String> entitySkillXpBlacklist =
            new ValidatedString("").toList(List.of());

    @Comment("Multiplier applied to skill XP earned against entities spawned by a mob spawner. 1.0 for no modification; 0.0 disables farming spawners entirely.")
    public ValidatedDouble spawnerMobSkillXpMultiplier = new ValidatedDouble(0.1, 1.0, 0.0);

    // --- Display ---

    @Comment("Draw the player level above the player's nameplate. Dynamic-Difficulty (if present) defers player nameplates to Chronicles automatically, so toggling this is the single source of truth.")
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


        public static StatModifierList defaultFor(String statId) {
            Map<String, List<StatModifierSpec>> defaults = Map.of(
                    ModStats.STRENGTH, List.of(
                            new StatModifierSpec(Identifier.parse("minecraft:attack_damage"), 0.5, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("minecraft:block_break_speed"), 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:arrow_velocity"), 0.02, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:ranged_crit_damage"), 0.05, AttributeModifier.Operation.ADD_VALUE)
                            // new StatModifierSpec(Identifier.parse("combat_attributes:stamina_regen"), 0.02, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.DEXTERITY, List.of(
                            new StatModifierSpec(Identifier.parse("combat_attributes:ranged_damage"), 0.5, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("minecraft:attack_speed"), 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:draw_speed"), 0.02, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("minecraft:movement_speed"), 0.01, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:melee_crit_damage"), 0.05, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.CONSTITUTION, List.of(
                            // new StatModifierSpec(Identifier.parse("combat_attributes:max_stamina"), 10, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("minecraft:max_health"), 1.0, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("minecraft:armor"), 0.2, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("minecraft:armor_toughness"), 0.1, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("minecraft:knockback_resistance"), 0.01, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:magic_resistance"), 0.2, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.INTELLIGENCE, List.of(
                            new StatModifierSpec(Identifier.parse("combat_attributes:magic_power"), 0.5, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:magic_crit_damage"), 0.05, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:lifesteal"), 0.01, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.WISDOM, List.of(
                            new StatModifierSpec(Identifier.parse("combat_attributes:accuracy"), 0.02, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:evasion"), 0.01, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:experience_gain"), 0.02, AttributeModifier.Operation.ADD_VALUE),
                            // new StatModifierSpec(Identifier.parse("combat_attributes:max_mana"), 10, AttributeModifier.Operation.ADD_VALUE),
                            // new StatModifierSpec(Identifier.parse("combat_attributes:mana_regen"), 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                            // Maybe just a balancing placeholder until mana lands
                            new StatModifierSpec(Identifier.parse("minecraft:entity_interaction_range"), 0.02, AttributeModifier.Operation.ADD_VALUE)
                    ),
                    ModStats.LUCKINESS, List.of(
                            new StatModifierSpec(Identifier.parse("minecraft:luck"), 0.2, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:melee_crit_chance"), 0.01, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:ranged_crit_chance"), 0.01, AttributeModifier.Operation.ADD_VALUE),
                            new StatModifierSpec(Identifier.parse("combat_attributes:magic_crit_chance"), 0.01, AttributeModifier.Operation.ADD_VALUE)
                    )
            );
            return new StatModifierList(defaults.getOrDefault(statId, List.of(new StatModifierSpec())));
        }
    }
}
