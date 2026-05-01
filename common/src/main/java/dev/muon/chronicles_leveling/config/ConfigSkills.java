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

    public Mining mining = new Mining();
    public Speech speech = new Speech();
    public Farming farming = new Farming();
    public Enchanting enchanting = new Enchanting();
    public Smithing smithing = new Smithing();
    public Fishing fishing = new Fishing();

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

    /** XP from breaking blocks. */
    public static class Mining extends Skill {
        @Comment("XP per block break. 'h' = block hardness (>= 0). Tweak the coefficient to control overall pacing.")
        public ValidatedExpression xpPerHardness = new ValidatedExpression("0.5 + h", Set.of('h'));

        @Comment("Multiplier applied to XP when the broken block is in the #c:ores tag and was NOT mined with Silk Touch. Ore breaks should out-pace generic stone.")
        public ValidatedDouble oreMultiplier = new ValidatedDouble(3.0, 100.0, 0.0);

        @Comment("Multiplier applied when an ore is broken with Silk Touch. 0.0 = no XP, 1.0 = same as a non-silk ore break, etc. Defaults to 0 so silk-stockpiling doesn't double-dip with the eventual smelt.")
        public ValidatedDouble silkTouchOreMultiplier = new ValidatedDouble(0.0, 100.0, 0.0);

        @Comment("Per-tier multipliers for ore/stone scaling. The first matching tag (top-down) wins. Default tags cover vanilla 1.21 mining tiers; pack authors can prepend custom tags (e.g. modded netherite-plus tiers) for higher-tier modded ores.")
        public ValidatedList<TierBonus> tierBonuses = new ValidatedAny<>(new TierBonus()).toList(List.of(
                new TierBonus(Identifier.fromNamespaceAndPath("minecraft", "incorrect_for_diamond_tool"), 5.0),
                new TierBonus(Identifier.fromNamespaceAndPath("minecraft", "needs_diamond_tool"), 3.0),
                new TierBonus(Identifier.fromNamespaceAndPath("minecraft", "needs_iron_tool"), 2.0),
                new TierBonus(Identifier.fromNamespaceAndPath("minecraft", "needs_stone_tool"), 1.25)
        ));
    }

    /** One row mapping a block tag to a tier multiplier; first match wins, so list stricter tags first. */
    public static class TierBonus implements Walkable {

        @Comment("Block tag id (e.g. minecraft:needs_iron_tool, c:ores/netherite_plus). Match is exact, so list both vanilla and modded tags as needed.")
        public ValidatedIdentifier tag;

        @Comment("Multiplier applied to base XP when the broken block has this tag.")
        public ValidatedDouble multiplier;

        public TierBonus() {
            this(Identifier.fromNamespaceAndPath("minecraft", "needs_stone_tool"), 1.0);
        }

        public TierBonus(Identifier tag, double multiplier) {
            this.tag = new ValidatedIdentifier(tag);
            this.multiplier = new ValidatedDouble(multiplier, 1000.0, 0.0);
        }
    }

    /** XP from completing villager trades. */
    public static class Speech extends Skill {
        @Comment("XP per completed trade. 'x' = the merchant XP value of the trade (vanilla novice = 2, master = 30). Set 'x' to 1 to award flat XP regardless of tier.")
        public ValidatedExpression xpPerTradeXp = new ValidatedExpression("2 * x", Set.of('x'));
    }

    /** XP from tilling, planting, and harvesting fully-grown crops. */
    public static class Farming extends Skill {
        @Comment("XP per block tilled with a hoe (dirt/grass/path -> farmland, etc.).")
        public ValidatedDouble xpPerTill = new ValidatedDouble(1.0, 1_000.0, 0.0);

        @Comment("XP per crop/seed planted by the player. Only counts seed-style placements (BushBlock and crop-tagged blocks); generic block placement does not award XP.")
        public ValidatedDouble xpPerPlant = new ValidatedDouble(2.0, 1_000.0, 0.0);

        @Comment("XP per harvested crop. Awarded only when the broken block is fully grown — partial-growth breaks give nothing, so misclicks aren't free XP.")
        public ValidatedDouble xpPerHarvest = new ValidatedDouble(5.0, 1_000.0, 0.0);
    }

    /** XP from enchanting tables, grindstone disenchants, and anvil combines. */
    public static class Enchanting extends Skill {
        @Comment("XP per successful enchanting-table use. 'c' = level cost of the chosen slot (1-30). The cost itself encodes both slot index and bookshelf count via vanilla's getEnchantmentCost formula, so it's the single power signal.")
        public ValidatedExpression xpPerTableEnchant = new ValidatedExpression("3 * c", Set.of('c'));

        @Comment("XP per grindstone disenchant. 'x' = experience the grindstone awards from removed enchantments. Set to 0 to disable grindstone XP.")
        public ValidatedExpression xpPerGrindstone = new ValidatedExpression("0.5 * x", Set.of('x'));

        @Comment("XP per anvil result taken. 'c' = level cost of the operation (anvil's level-cost number). Includes repair, rename, and combine — pack can split via cost thresholds in the formula.")
        public ValidatedExpression xpPerAnvil = new ValidatedExpression("c", Set.of('c'));
    }

    /** XP from crafting; tier multipliers stack on top of the base for recognized tools/armor. */
    public static class Smithing extends Skill {
        @Comment("Base XP per crafted item. Awarded on every craft (vanilla or smithing-table) regardless of recipe; tier multipliers stack on top for tools/armor.")
        public ValidatedDouble baseXp = new ValidatedDouble(1.0, 100_000.0, 0.0);

        @Comment("Multiplier applied to base XP when the result is a unit/stack of N items. 'n' = stack size at the time of taking. Default keeps stack-output recipes from out-pacing single-item ones.")
        public ValidatedExpression stackMultiplier = new ValidatedExpression("max(1, sqrt(n))", Set.of('n'));

        @Comment("Multiplier for wood-tier tools.")
        public ValidatedDouble woodMultiplier = new ValidatedDouble(1.0, 1000.0, 0.0);

        @Comment("Multiplier for stone-tier tools.")
        public ValidatedDouble stoneMultiplier = new ValidatedDouble(1.5, 1000.0, 0.0);

        @Comment("Multiplier for copper-tier tools.")
        public ValidatedDouble copperMultiplier = new ValidatedDouble(2.0, 1000.0, 0.0);

        @Comment("Multiplier for gold-tier tools.")
        public ValidatedDouble goldMultiplier = new ValidatedDouble(2.0, 1000.0, 0.0);

        @Comment("Multiplier for iron-tier tools and armor.")
        public ValidatedDouble ironMultiplier = new ValidatedDouble(3.0, 1000.0, 0.0);

        @Comment("Multiplier for diamond-tier tools and armor.")
        public ValidatedDouble diamondMultiplier = new ValidatedDouble(5.0, 1000.0, 0.0);

        @Comment("Multiplier for netherite-tier tools and armor.")
        public ValidatedDouble netheriteMultiplier = new ValidatedDouble(8.0, 1000.0, 0.0);
    }

    /** XP from fishing. Vanilla's fishing event/mixin doesn't expose which loot table fired,
     *  so each catch is classified by config — items not in either list fall through to junk. */
    public static class Fishing extends Skill {
        @Comment("XP per fish-class catch (cod, salmon, etc.). Items matching #minecraft:fishes by default.")
        public ValidatedDouble fishXp = new ValidatedDouble(5.0, 100_000.0, 0.0);

        @Comment("XP per treasure catch (saddles, name tags, enchanted books, ...). Items matching the vanilla treasure loot table by default.")
        public ValidatedDouble treasureXp = new ValidatedDouble(20.0, 100_000.0, 0.0);

        @Comment("XP for any catch that isn't classified as fish or treasure (rotten flesh, sticks, lily pads, ...). Set to 0 to disable junk XP entirely.")
        public ValidatedDouble junkXp = new ValidatedDouble(1.0, 100_000.0, 0.0);

        @Comment("Items or tags classified as 'fish'. Tag entries take a leading '#' (e.g. '#minecraft:fishes'); item ids stand alone (e.g. 'mymod:exotic_carp'). Default is the vanilla #minecraft:fishes tag.")
        public ValidatedList<String> fishItems = new ValidatedString("").toList(List.of(
                "#minecraft:fishes"
        ));

        @Comment("Items or tags classified as 'treasure'. Same '#tag'/'item' rule as fishItems. Default mirrors the vanilla gameplay/fishing/treasure loot table outputs.")
        public ValidatedList<String> treasureItems = new ValidatedString("").toList(List.of(
                "minecraft:bow",
                "minecraft:enchanted_book",
                "minecraft:fishing_rod",
                "minecraft:name_tag",
                "minecraft:nautilus_shell",
                "minecraft:saddle"
        ));
    }
}
