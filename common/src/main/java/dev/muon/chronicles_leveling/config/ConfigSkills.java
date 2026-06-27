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
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
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
 *   <li><b>General</b>: XP blacklist + spawner multiplier shared by all skills.</li>
 *   <li><b>Per-skill sections</b>: one {@link ConfigSection} per skill in
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

    private static final String DEFAULT_CURVE = "55 + 0.8 * (l - 1)^2.7";

    // --- General ---

    @Comment("Entity ids that grant no skill XP when damaged or killed (e.g. minecraft:armor_stand). Format: 'namespace:path'.")
    public ValidatedList<String> entityXpBlacklist = new ValidatedString("").toList(List.of());

    @Comment("Multiplier applied to skill XP earned against entities spawned by a mob spawner. 1.0 for no modification; 0.0 disables farming spawners entirely.")
    public ValidatedDouble spawnerMobMultiplier = new ValidatedDouble(0.1, 1.0, 0.0);

    @Comment("Per-hit damage clamp fed into combat XP formulas. Caps a single hit so one absurd source can't instantly max a skill. Raise for high-end modpacks if you'd rather not clamp.")
    public ValidatedDouble maxDamagePerHit = new ValidatedDouble(10_000.0, 1_000_000.0, 0.0);

    @Comment("Hard cap on any single skill's level. XP past the cap is dropped. 0 to disable the cap entirely.")
    public ValidatedInt maxSkillLevel = new ValidatedInt(100, 10_000, 0);

    @Comment("When true, an arrow fired by a PLAYER does not ADD its own post-hit invulnerability window (it preserves whatever was there), so a Multishot/rapid volley all connects on the same target. An i-frame window already running from another source is still respected, and mob-shot arrows are unaffected. Turn off for stricter vanilla combat.")
    public ValidatedBoolean arrowsIgnoreInvulnerability = new ValidatedBoolean(true);

    // --- Per-skill sections ---

    public Weaponry weaponry = new Weaponry();
    public Archery archery = new Archery();
    public Magic magic = new Magic();
    public Defense defense = new Defense();
    public Acrobatics acrobatics = new Acrobatics();
    public Alchemy alchemy = new Alchemy();

    public Mining mining = new Mining();
    public Speech speech = new Speech();
    public Herbalism herbalism = new Herbalism();
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
            case Skills.DEFENSE    -> defense;
            case Skills.ACROBATICS -> acrobatics;
            case Skills.ALCHEMY    -> alchemy;
            case Skills.MINING     -> mining;
            case Skills.SPEECH     -> speech;
            case Skills.HERBALISM  -> herbalism;
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

    /** XP from non-magic melee damage dealt, plus the combat-proc tuning for Weaponry perks. */
    public static class Weaponry extends Skill {
        @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
        public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));

        // --- Combat-proc tuning (perk behaviors; consumed by CombatProcRouter) ---
        @Comment("Executioner: base execute window as a fraction of the target's max health, before rank widening.")
        public ValidatedDouble executionerWindowBase = new ValidatedDouble(0.20, 1.0, 0.0);
        @Comment("Executioner: extra execute-window width per point of summed bonus, so the window grows with rank.")
        public ValidatedDouble executionerWindowPerBonus = new ValidatedDouble(0.50, 100.0, 0.0);
        @Comment("Executioner: hard ceiling on the execute window (fraction of max health).")
        public ValidatedDouble executionerWindowMax = new ValidatedDouble(0.50, 1.0, 0.0);
        @Comment("Momentum: maximum consecutive-hit stacks per rank that ramp damage (rank 3 = 3x this many).")
        public ValidatedInt momentumMaxStacksPerRank = new ValidatedInt(5, 1000, 1);
        @Comment("Momentum: ticks without hitting the same target before the streak resets (20 = 1s).")
        public ValidatedInt momentumResetTicks = new ValidatedInt(100, 6000, 1);
        @Comment("Skewer: extra knockback strength on a piercing-weapon hit.")
        public ValidatedDouble skewerKnockback = new ValidatedDouble(0.5, 10.0, 0.0);
        @Comment("Rend bleed: per-tick damage at one stack (ticks once per second).")
        public ValidatedDouble rendBleedDamageBase = new ValidatedDouble(1.0, 1000.0, 0.0);
        @Comment("Rend bleed: additional per-tick damage per extra stack.")
        public ValidatedDouble rendBleedDamagePerStack = new ValidatedDouble(0.5, 1000.0, 0.0);
        @Comment("Rend bleed: duration in ticks applied/refreshed per slashing hit (20 = 1s).")
        public ValidatedInt rendBleedDurationTicks = new ValidatedInt(100, 6000, 1);
        @Comment("Rend bleed: maximum stacks (deeper bleed = more per-tick damage).")
        public ValidatedInt rendBleedMaxStacks = new ValidatedInt(4, 100, 1);
        @Comment("Sunder: armor-shred debuff duration in ticks per blunt hit (20 = 1s).")
        public ValidatedInt sunderDurationTicks = new ValidatedInt(120, 6000, 1);
        @Comment("Sunder: fraction of armor shredded PER STACK (stacks deepen the same debuff). Baked into the effect at registration; restart to apply a change.")
        public ValidatedDouble sunderArmorShred = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Sunder: maximum stacks a target can accumulate; each blunt hit adds one and total shred = stacks x sunderArmorShred.")
        public ValidatedInt sunderMaxStacks = new ValidatedInt(5, 100, 1);
        @Comment("Concussive Blows: stamina drained from a PLAYER target per blunt hit (Combat-Attributes; no effect when CA is absent).")
        public ValidatedDouble concussiveStaminaDrain = new ValidatedDouble(4.0, 100_000.0, 0.0);
        @Comment("Concussive Blows: slowness (stagger) duration in ticks per blunt hit (20 = 1s).")
        public ValidatedInt concussiveSlownessTicks = new ValidatedInt(40, 6000, 1);
        @Comment("Concussive Blows: slowness amplifier (0 = Slowness I).")
        public ValidatedInt concussiveSlownessAmplifier = new ValidatedInt(0, 255, 0);
        @Comment("Seismic Slam: cooldown in ticks for the active (20 = 1s).")
        public ValidatedInt seismicSlamCooldownTicks = new ValidatedInt(140, 100_000, 1);
        @Comment("Seismic Slam: stamina cost to activate (Combat-Attributes).")
        public ValidatedDouble seismicSlamStaminaCost = new ValidatedDouble(20.0, 100_000.0, 0.0);
        @Comment("Seismic Slam: radius in blocks of the AoE around the player.")
        public ValidatedDouble seismicSlamRadius = new ValidatedDouble(4.0, 64.0, 0.0);
        @Comment("Seismic Slam: base damage dealt to each enemy. Lands as a real melee hit, so your damage modifiers scale it.")
        public ValidatedDouble seismicSlamDamage = new ValidatedDouble(6.0, 100_000.0, 0.0);
        @Comment("Seismic Slam: stagger (Slowness) duration in ticks applied to each enemy hit (20 = 1s).")
        public ValidatedInt seismicSlamSlowTicks = new ValidatedInt(60, 6000, 1);
        @Comment("Seismic Slam: stagger amplifier (2 = Slowness III).")
        public ValidatedInt seismicSlamSlowAmplifier = new ValidatedInt(2, 255, 0);
        @Comment("Master's Focus: active duration in ticks during which every melee hit crits (20 = 1s).")
        public ValidatedInt mastersFocusDurationTicks = new ValidatedInt(100, 6000, 1);
        @Comment("Master's Focus: fraction of a hit's damage added as armor/resistance-bypassing true damage when it would already crit.")
        public ValidatedDouble mastersFocusTrueDamageFraction = new ValidatedDouble(0.10, 10.0, 0.0);

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Slashing Focus: bonus slashing-weapon damage per rank (0.10 = +10% per rank).")
        public ValidatedDouble slashingDamagePerRank = new ValidatedDouble(0.10, 10.0, 0.0);
        @Comment("Piercing Focus: bonus piercing-weapon damage per rank.")
        public ValidatedDouble piercingDamagePerRank = new ValidatedDouble(0.10, 10.0, 0.0);
        @Comment("Blunt Focus: bonus blunt-weapon damage per rank.")
        public ValidatedDouble bluntDamagePerRank = new ValidatedDouble(0.10, 10.0, 0.0);
        @Comment("Quick Blade: bonus attack speed per rank, applied PER flurry stack (consecutive slashing hits stack it).")
        public ValidatedDouble quickBladeAttackSpeedPerRank = new ValidatedDouble(0.05, 10.0, 0.0);
        @Comment("Quick Blade: maximum flurry stacks per rank (each consecutive slashing hit adds one; 3 ranks -> 5/10/15).")
        public ValidatedInt quickBladeFlurryMaxStacksPerRank = new ValidatedInt(5, 100, 1);
        @Comment("Quick Blade: ticks without a slashing hit before the flurry decays (20 = 1s).")
        public ValidatedInt quickBladeFlurryResetTicks = new ValidatedInt(60, 6000, 1);
        @Comment("Rend: chance (0-1) for a slashing hit to apply a bleed stack.")
        public ValidatedDouble rendChance = new ValidatedDouble(0.20, 1.0, 0.0);
        @Comment("Riposte: chance (0-1) to auto-counter a melee hit taken.")
        public ValidatedDouble riposteChance = new ValidatedDouble(0.15, 1.0, 0.0);
        @Comment("Armor Pierce: fraction of target armor ignored per rank.")
        public ValidatedDouble armorPiercePerRank = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Executioner: bonus damage per rank vs targets within the execute window.")
        public ValidatedDouble executionerBonusPerRank = new ValidatedDouble(0.15, 10.0, 0.0);
        @Comment("Heavy Hitter: bonus damage per rank as a fraction of the attacker's max health.")
        public ValidatedDouble heavyHitterMaxHpFractionPerRank = new ValidatedDouble(0.02, 1.0, 0.0);
        @Comment("Momentum: per-rank damage ramp added for each consecutive-hit stack.")
        public ValidatedDouble momentumRampPerRank = new ValidatedDouble(0.03, 10.0, 0.0);
        @Comment("Bloodthirst: lifesteal per skill level (maps to combat_attributes:lifesteal).")
        public ValidatedDouble bloodthirstLifestealPerLevel = new ValidatedDouble(0.004, 10.0, 0.0);
        @Comment("Bloodthirst: lifesteal cap.")
        public ValidatedDouble bloodthirstLifestealCap = new ValidatedDouble(0.10, 100.0, 0.0);
    }

    /** XP from non-magic projectile damage dealt, plus the projectile-perk tuning for Archery. */
    public static class Archery extends Skill {
        @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
        public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));

        // --- Combat-proc tuning (perk behaviors; consumed by CombatProcRouter / ArcheryHooks) ---
        @Comment("Far Shot: arrow travel distance (blocks) at which the bonus reaches full; scales linearly up to here.")
        public ValidatedDouble farShotMaxRange = new ValidatedDouble(30.0, 1000.0, 1.0);
        @Comment("Multishot: angular spread in degrees between each extra arrow.")
        public ValidatedDouble multishotSpreadDegrees = new ValidatedDouble(10.0, 90.0, 0.0);
        @Comment("Ricochet: search radius (blocks) for the second target.")
        public ValidatedDouble ricochetRange = new ValidatedDouble(12.0, 64.0, 1.0);
        @Comment("Ricochet: launch speed of the bounced bolt.")
        public ValidatedDouble ricochetSpeed = new ValidatedDouble(1.5, 10.0, 0.1);
        @Comment("Ricochet: fraction of the source arrow's base damage carried by the bounced bolt.")
        public ValidatedDouble ricochetDamageFraction = new ValidatedDouble(0.6, 1.0, 0.0);
        @Comment("Multishot/Ricochet clones (the spread + bounce bolts; they can't be picked up) despawn this many "
                + "ground-ticks after they stick, vs. vanilla's 1200 (60s). Only counts while stuck, not in flight; 1200 = vanilla.")
        public ValidatedInt clonedProjectileDespawnTicks = new ValidatedInt(60, 1200, 1);
        @Comment("Disorient: blindness + nausea duration in ticks (20 = 1s).")
        public ValidatedInt disorientDurationTicks = new ValidatedInt(80, 6000, 1);
        @Comment("Pinning: slowness duration in ticks (20 = 1s).")
        public ValidatedInt pinningDurationTicks = new ValidatedInt(60, 6000, 1);
        @Comment("Pinning: slowness amplifier (0 = Slowness I, 2 = Slowness III).")
        public ValidatedInt pinningAmplifier = new ValidatedInt(2, 255, 0);

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Strong Arm: bonus arrow velocity per skill level.")
        public ValidatedDouble arrowVelocityPerLevel = new ValidatedDouble(0.01, 10.0, 0.0);
        @Comment("Strong Arm: arrow-velocity bonus cap.")
        public ValidatedDouble arrowVelocityCap = new ValidatedDouble(1.0, 100.0, 0.0);
        @Comment("Quick Hands: bonus draw speed per skill level.")
        public ValidatedDouble drawSpeedPerLevel = new ValidatedDouble(0.01, 10.0, 0.0);
        @Comment("Quick Hands: draw-speed bonus cap.")
        public ValidatedDouble drawSpeedCap = new ValidatedDouble(1.0, 100.0, 0.0);
        @Comment("Far Shot: bonus damage fraction at maximum arrow travel.")
        public ValidatedDouble farShotBonus = new ValidatedDouble(0.5, 10.0, 0.0);
        @Comment("Bullseye: bonus ranged crit chance per skill level.")
        public ValidatedDouble rangedCritChancePerLevel = new ValidatedDouble(0.005, 10.0, 0.0);
        @Comment("Bullseye: ranged crit chance cap.")
        public ValidatedDouble rangedCritChanceCap = new ValidatedDouble(0.25, 1.0, 0.0);
        @Comment("Bullseye: bonus ranged crit damage per skill level.")
        public ValidatedDouble rangedCritDamagePerLevel = new ValidatedDouble(0.01, 10.0, 0.0);
        @Comment("Bullseye: ranged crit damage cap.")
        public ValidatedDouble rangedCritDamageCap = new ValidatedDouble(0.5, 100.0, 0.0);
        @Comment("Marksman's Eye: flat accuracy bonus.")
        public ValidatedDouble accuracyBonus = new ValidatedDouble(0.15, 10.0, 0.0);
        @Comment("Disorient: chance (0-1) for a hit to apply blindness + nausea.")
        public ValidatedDouble disorientChance = new ValidatedDouble(0.2, 1.0, 0.0);
        @Comment("Pinning Shot: chance (0-1) for a hit to apply slowness.")
        public ValidatedDouble pinningChance = new ValidatedDouble(0.2, 1.0, 0.0);
        @Comment("Multishot: extra arrows fired per rank (floored to a whole number).")
        public ValidatedDouble multishotArrowsPerRank = new ValidatedDouble(1.0, 10.0, 0.0);
        @Comment("Ricochet: extra bounces per rank (floored to a whole number).")
        public ValidatedDouble ricochetBouncesPerRank = new ValidatedDouble(1.0, 10.0, 0.0);
    }

    /** XP from magic damage dealt; anything tagged {@code #c:is_magic}. */
    public static class Magic extends Skill {
        @Comment("XP awarded per hit. 'd' = damage amount (pre-mitigation).")
        public ValidatedExpression xpPerDamage = new ValidatedExpression("d", Set.of('d'));
    }

    /** XP from damage taken; pre-mitigation so heavy armor doesn't stall the skill. Plus Pain Tolerance tuning. */
    public static class Defense extends Skill {
        @Comment("XP awarded per hit taken. 'd' = damage amount, pre-mitigation.")
        public ValidatedExpression xpPerDamageTaken = new ValidatedExpression("d", Set.of('d'));

        @Comment("Pain Tolerance: a single hit can never be capped below this fraction of max health (sensible floor).")
        public ValidatedDouble painToleranceFloor = new ValidatedDouble(0.5, 1.0, 0.0);

        @Comment("Last Stand: health fraction at or below which it triggers (0.30 = 30% of max health).")
        public ValidatedDouble lastStandThreshold = new ValidatedDouble(0.30, 1.0, 0.0);
        @Comment("Last Stand: absorption + resistance duration in ticks (20 = 1s).")
        public ValidatedInt lastStandDurationTicks = new ValidatedInt(140, 6000, 1);
        @Comment("Last Stand: absorption amplifier (0 = Absorption I = +4 health of shield).")
        public ValidatedInt lastStandAbsorptionAmplifier = new ValidatedInt(1, 255, 0);
        @Comment("Last Stand: resistance amplifier (1 = Resistance II = 40% damage reduction; 4 caps at full immunity).")
        public ValidatedInt lastStandResistanceAmplifier = new ValidatedInt(1, 4, 0);
        @Comment("Last Stand: cooldown in ticks before it can trigger again (1200 = 60s).")
        public ValidatedInt lastStandCooldownTicks = new ValidatedInt(1200, 100_000, 1);

        @Comment("Shield Bash: slowness (stun) duration in ticks applied to the attacker on a bash (20 = 1s).")
        public ValidatedInt shieldBashStunTicks = new ValidatedInt(40, 6000, 1);
        @Comment("Shield Bash: slowness amplifier on the stunned attacker (2 = Slowness III).")
        public ValidatedInt shieldBashStunAmplifier = new ValidatedInt(2, 255, 0);
        @Comment("Shield Bash: knockback strength applied to the attacker on a bash.")
        public ValidatedDouble shieldBashKnockback = new ValidatedDouble(1.0, 10.0, 0.0);

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Iron Skin: bonus armor per skill level, multiplied by rank (3 ranks -> 0.1/0.2/0.3 per level).")
        public ValidatedDouble ironSkinArmorPerLevel = new ValidatedDouble(0.10, 10.0, 0.0);
        @Comment("Iron Skin: armor bonus cap (per rank; rank 3 caps at this x3).")
        public ValidatedDouble ironSkinArmorCap = new ValidatedDouble(4.0, 100.0, 0.0);
        @Comment("Magic Ward: bonus magic defense per skill level, multiplied by rank (3 ranks -> 0.1/0.2/0.3 per level).")
        public ValidatedDouble magicWardPerLevel = new ValidatedDouble(0.10, 10.0, 0.0);
        @Comment("Magic Ward: magic defense bonus cap (per rank; rank 3 caps at this x3).")
        public ValidatedDouble magicWardCap = new ValidatedDouble(4.0, 100.0, 0.0);
        @Comment("Pain Tolerance: per-rank fraction of max health a single hit is capped to.")
        public ValidatedDouble painToleranceFractionPerRank = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Shield Master: base block arc in degrees (vanilla is 180).")
        public ValidatedDouble wideBlockArcBaseDegrees = new ValidatedDouble(180.0, 360.0, 0.0);
        @Comment("Shield Master: extra block arc in degrees per rank.")
        public ValidatedDouble wideBlockArcPerRankDegrees = new ValidatedDouble(60.0, 180.0, 0.0);
        @Comment("Shield Master: shield-bash chance (0-1) per rank.")
        public ValidatedDouble shieldBashChancePerRank = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Retribution: fraction of blocked damage reflected per rank.")
        public ValidatedDouble retributionReflectPerRank = new ValidatedDouble(0.10, 10.0, 0.0);
    }

    /** XP from jumping (flat) and fall damage taken (formula). */
    public static class Acrobatics extends Skill {
        @Comment("XP awarded per jump. Tiny on purpose; pure idle activity.")
        public ValidatedDouble xpPerJump = new ValidatedDouble(0.05, 100.0, 0.0);

        @Comment("XP awarded per point of fall damage taken (pre-mitigation). 'd' = damage.")
        public ValidatedExpression xpPerFallDamage = new ValidatedExpression("d", Set.of('d'));

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Feather Fall: bonus safe-fall distance (blocks) per skill level.")
        public ValidatedDouble safeFallPerLevel = new ValidatedDouble(0.05, 10.0, 0.0);
        @Comment("Feather Fall: safe-fall distance bonus cap.")
        public ValidatedDouble safeFallCap = new ValidatedDouble(5.0, 1000.0, 0.0);
        @Comment("Roll: fall-damage reduction (0-1) at the first rank, applied when landing looking down.")
        public ValidatedDouble rollReductionStart = new ValidatedDouble(0.50, 1.0, 0.0);
        @Comment("Roll: added fall-damage reduction (0-1) per rank past the first.")
        public ValidatedDouble rollReductionPerLevel = new ValidatedDouble(0.25, 1.0, 0.0);
        @Comment("Roll: minimum downward pitch (degrees, 90 = straight down) required to roll on landing.")
        public ValidatedDouble rollLookDownPitch = new ValidatedDouble(70.0, 90.0, 0.0);
        @Comment("Spring Step: bonus jump strength (fraction of base).")
        public ValidatedDouble jumpStrengthBonus = new ValidatedDouble(0.15, 10.0, 0.0);
        @Comment("Spring Step: bonus step height (blocks).")
        public ValidatedDouble stepHeightBonus = new ValidatedDouble(0.5, 10.0, 0.0);
        @Comment("Dodge: CA dodge chance (0-1) per rank (2 ranks -> 5/10%).")
        public ValidatedDouble evasionPerRank = new ValidatedDouble(0.05, 1.0, 0.0);
        @Comment("Catlike: bonus sneaking speed per rank (fraction of base; 3 ranks -> 25/50/75%).")
        public ValidatedDouble sneakingSpeedBonus = new ValidatedDouble(0.25, 10.0, 0.0);
        @Comment("Catlike: while sneaking, fraction (0-1) per rank that a holder's visibility to targeting mobs shrinks (3 ranks -> 30/60/90%).")
        public ValidatedDouble catlikeDetectionReductionPerRank = new ValidatedDouble(0.30, 1.0, 0.0);
        @Comment("Momentum Vault: movement-speed burst duration (ticks) on a sprint-jump (20 = 1s).")
        public ValidatedInt momentumVaultSpeedTicks = new ValidatedInt(30, 6000, 0);
        @Comment("Momentum Vault: Speed effect amplifier (0 = Speed I) for the burst.")
        public ValidatedInt momentumVaultSpeedAmplifier = new ValidatedInt(1, 10, 0);
        @Comment("Dash: invulnerability (i-frame) window in ticks the lunge grants (20 = 1s).")
        public ValidatedInt dashIframeTicks = new ValidatedInt(8, 6000, 0);
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

        @Comment("Effect ids excluded from the Experimental/Volatile Elixir roll pools (e.g. minecraft:luck). Format: 'namespace:path'. The pools are otherwise every registered effect of the matching category.")
        public ValidatedList<String> elixirEffectBlacklist = new ValidatedString("").toList(List.of());

        @Comment("Toxicologist: how many nearby enemies a qualifying kill spreads the victim's harmful effects to.")
        public ValidatedInt toxicologistSpreadTargets = new ValidatedInt(2, 16, 1);

        @Comment("Toxicologist: search radius (blocks) around the victim for spread targets.")
        public ValidatedDouble toxicologistSpreadRadius = new ValidatedDouble(8.0, 64.0, 1.0);

        @Comment("Max stack size for potions (drinkable, splash, lingering). 1 keeps vanilla non-stacking. Applied at item registration, so a change needs a restart. Capped at 99 (the vanilla stack-size component ceiling).")
        public ValidatedInt maxPotionStackSize = new ValidatedInt(16, 99, 1);

        @Comment("Master toggle for stackable potions. When false, potions keep their vanilla stack size regardless of maxPotionStackSize. Applied at item registration; restart to change.")
        public ValidatedBoolean stackablePotions = new ValidatedBoolean(true);

        @Comment("Cooldown in ticks applied after THROWING a splash/lingering potion (20 = 1s); briefly blocks all potions of that thrown type, not just the one used. 0 disables. Anti-spam for stackable potions; the Deft Hands perk and creative mode ignore it.")
        public ValidatedInt potionThrowCooldownTicks = new ValidatedInt(10, 200, 0);

        @Comment("Cooldown in ticks applied after DRINKING an effect potion (20 = 1s); briefly blocks all drinkable potions. No-effect potions (water, awkward) are exempt. 0 disables. The Deft Hands perk and creative mode ignore it.")
        public ValidatedInt potionDrinkCooldownTicks = new ValidatedInt(10, 200, 0);

        @Comment("Deft Hands: throw-velocity multiplier for splash/lingering potions the perk holder throws (range scales with it; 3 = three times as far).")
        public ValidatedDouble deftHandsThrowMultiplier = new ValidatedDouble(3.0, 100.0, 0.0);

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Catalysis: bonus brewing speed per rank.")
        public ValidatedDouble brewSpeedPerRank = new ValidatedDouble(0.25, 10.0, 0.0);
        @Comment("Catalysis: brewing-fuel-save chance (0-1) per rank.")
        public ValidatedDouble fuelSavePerRank = new ValidatedDouble(0.25, 1.0, 0.0);
        @Comment("Lingering Touch: bonus lingering-cloud duration/area per rank.")
        public ValidatedDouble lingeringTouchPerRank = new ValidatedDouble(0.50, 10.0, 0.0);
        @Comment("Iron Stomach: fraction of negative drink effects reduced.")
        public ValidatedDouble ironStomachReduction = new ValidatedDouble(0.50, 1.0, 0.0);
        @Comment("Master Brewer: chance (0-1) per rank to brew a double output (also doubles Experimental Elixirs).")
        public ValidatedDouble extraBrewChancePerRank = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Alchemy school (Restoration/Negation): bonus skill XP gain per skill level; shared by both school roots.")
        public ValidatedDouble schoolExperienceGainPerLevel = new ValidatedDouble(0.004, 10.0, 0.0);
        @Comment("Alchemy school: skill XP gain bonus cap.")
        public ValidatedDouble schoolExperienceGainCap = new ValidatedDouble(0.20, 100.0, 0.0);
        @Comment("Quick Quaff: bonus drink speed per rank.")
        public ValidatedDouble quickQuaffPerRank = new ValidatedDouble(0.25, 10.0, 0.0);
        @Comment("Empowered Splash: bonus splash-potion potency/radius.")
        public ValidatedDouble empoweredSplashBonus = new ValidatedDouble(0.50, 10.0, 0.0);
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

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Vein Sense: bonus mining efficiency per skill level.")
        public ValidatedDouble veinSenseEfficiencyPerLevel = new ValidatedDouble(0.04, 10.0, 0.0);
        @Comment("Vein Sense: mining efficiency bonus cap.")
        public ValidatedDouble veinSenseEfficiencyCap = new ValidatedDouble(4.0, 1000.0, 0.0);
        @Comment("Mother Lode: extra ore-drop chance (0-1) per rank.")
        public ValidatedDouble extraOreDropPerRank = new ValidatedDouble(0.05, 1.0, 0.0);
        @Comment("Prospector: effective Fortune level added to ore drops per rank (+1/+2/+3 at ranks 1-3).")
        public ValidatedDouble naturalFortunePerRank = new ValidatedDouble(1.0, 100.0, 0.0);
        @Comment("Gem Hunter: bonus rare-ore drop chance (0-1).")
        public ValidatedDouble rareOreBonus = new ValidatedDouble(0.5, 1.0, 0.0);
        @Comment("Deep Diver: bonus submerged mining speed (fraction of base).")
        public ValidatedDouble deepDiverSubmergedSpeed = new ValidatedDouble(0.5, 10.0, 0.0);
        @Comment("Sturdy Tools: fraction of mining-tool durability damage avoided.")
        public ValidatedDouble toolDurabilitySave = new ValidatedDouble(0.5, 1.0, 0.0);
        @Comment("Cave Eyes: night-vision brightness (0-1) granted while not exposed to the sky (0.4 = 40% of full night vision).")
        public ValidatedDouble caveEyesNightVisionScale = new ValidatedDouble(0.4, 1.0, 0.0);

        // --- Active-ability tuning ---
        @Comment("Smelter's Touch (active): duration in ticks during which mined ores drop smelted (20 = 1s).")
        public ValidatedInt smeltersTouchDurationTicks = new ValidatedInt(200, 6000, 1);
        @Comment("Smelter's Touch (active): cooldown in ticks.")
        public ValidatedInt smeltersTouchCooldownTicks = new ValidatedInt(600, 100_000, 1);
        @Comment("Superbreaker (active): instant-break duration in ticks PER RANK (4/8/12s at ranks 1-3 by default).")
        public ValidatedInt superbreakerDurationPerRankTicks = new ValidatedInt(80, 6000, 1);
        @Comment("Superbreaker (active): cooldown in ticks.")
        public ValidatedInt superbreakerCooldownTicks = new ValidatedInt(1200, 100_000, 1);
        @Comment("Vein Sense (active): ore-sight duration in ticks (20 = 1s).")
        public ValidatedInt veinSightDurationTicks = new ValidatedInt(300, 6000, 1);
        @Comment("Vein Sense (active): cooldown in ticks.")
        public ValidatedInt veinSightCooldownTicks = new ValidatedInt(600, 100_000, 1);
        @Comment("Vein Sense (active): half-extent (blocks) of the cuboid ore scan around the player. Larger shows more ores but makes the periodic scan heavier.")
        public ValidatedInt veinSightRadius = new ValidatedInt(16, 48, 1);
        @Comment("Vein Sense (active): minimum ticks between ore rescans (also rescans when you cross into a new chunk).")
        public ValidatedInt veinSightRescanIntervalTicks = new ValidatedInt(10, 200, 1);
        @Comment("Vein Sense (active): ARGB hex outline color used when an ore's texture has no clearly chromatic color (coal, iron, etc.).")
        public ValidatedString veinSightFallbackColor = new ValidatedString("FFAAAAAA");
        @Comment("Vein Sense (active): per-block outline color overrides (ARGB hex). Overrides the texture-derived color; any block listed here is highlighted even if it is not in #c:ores.")
        public ValidatedList<OreColor> veinSightColorOverrides = new ValidatedAny<>(new OreColor()).toList(List.of(
                new OreColor(Identifier.fromNamespaceAndPath("minecraft", "coal_ore"), "FF666666"),
                new OreColor(Identifier.fromNamespaceAndPath("minecraft", "deepslate_coal_ore"), "FF666666")
        ));
    }

    /** One row mapping a block id to an ARGB-hex Vein Sight outline color; overrides the texture-derived color. */
    public static class OreColor implements Walkable {

        @Comment("Block id to recolor (e.g. minecraft:redstone_ore).")
        public ValidatedIdentifier block;

        @Comment("ARGB hex color, e.g. FFFF0000 for opaque red.")
        public ValidatedString hex;

        public OreColor() {
            this(Identifier.fromNamespaceAndPath("minecraft", "coal_ore"), "FF666666");
        }

        public OreColor(Identifier block, String hex) {
            this.block = new ValidatedIdentifier(block);
            this.hex = new ValidatedString(hex);
        }
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

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Haggler: per rank, fraction fewer emeralds paid (emerald-cost offers) and more emeralds received (3 ranks -> 10/20/30%).")
        public ValidatedDouble tradeDiscountPerRank = new ValidatedDouble(0.1, 1.0, 0.0);
        @Comment("Master Negotiator: bonus villager XP from completed trades.")
        public ValidatedDouble villagerXpBonus = new ValidatedDouble(0.5, 100.0, 0.0);
        @Comment("Silver Tongue: chance (0-1) per rank a trade does not consume its stock (2 ranks -> 25/50%).")
        public ValidatedDouble noStockConsumeChance = new ValidatedDouble(0.25, 1.0, 0.0);
        @Comment("Enchanted Trader: enchantment power level applied to each extra enchantment rolled onto a trade result.")
        public ValidatedInt enchantedTraderLevel = new ValidatedInt(8, 100, 1);
        @Comment("XP for taming a tameable animal.")
        public ValidatedDouble xpPerTame = new ValidatedDouble(10.0, 100_000.0, 0.0);
        @Comment("XP for breeding a pair of animals.")
        public ValidatedDouble xpPerBreed = new ValidatedDouble(5.0, 100_000.0, 0.0);
        @Comment("Pack Leader: incoming-damage reduction (0-1) per rank for your tamed pets (3 ranks -> 10/20/30%).")
        public ValidatedDouble packLeaderReductionPerRank = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Kindred Fury: radius (blocks) within which your pets count toward the damage bonus.")
        public ValidatedDouble kindredFuryRadius = new ValidatedDouble(10.0, 256.0, 1.0);
        @Comment("Kindred Fury: bonus outgoing damage (0-1) granted to you and each pet, per nearby pet you own.")
        public ValidatedDouble kindredFuryPerPet = new ValidatedDouble(0.05, 10.0, 0.0);
        @Comment("Kindred Fury: maximum nearby pets that count toward the damage bonus (caps run-away pack scaling).")
        public ValidatedInt kindredFuryMaxPets = new ValidatedInt(5, 1024, 1);
        @Comment("Husbandry: fraction (0-1) of the post-breeding cooldown removed when you bred the pair.")
        public ValidatedDouble husbandryCooldownReduction = new ValidatedDouble(0.5, 1.0, 0.0);
        @Comment("Beast Whisperer: ability cooldown in ticks (20 ticks = 1 second).")
        public ValidatedInt beastWhispererCooldownTicks = new ValidatedInt(600, 1_000_000, 0);
        @Comment("Beast Whisperer: stamina cost to activate.")
        public ValidatedDouble beastWhispererStaminaCost = new ValidatedDouble(20.0, 10_000.0, 0.0);
        @Comment("Beast Whisperer: reach (blocks) of the instant-tame raycast.")
        public ValidatedDouble beastWhispererRange = new ValidatedDouble(8.0, 64.0, 1.0);
        @Comment("Pacify: ability cooldown in ticks (20 ticks = 1 second).")
        public ValidatedInt pacifyCooldownTicks = new ValidatedInt(800, 1_000_000, 0);
        @Comment("Pacify: stamina cost to activate.")
        public ValidatedDouble pacifyStaminaCost = new ValidatedDouble(30.0, 10_000.0, 0.0);
        @Comment("Pacify: radius (blocks) at rank 1.")
        public ValidatedDouble pacifyRadiusBase = new ValidatedDouble(6.0, 256.0, 1.0);
        @Comment("Pacify: extra radius (blocks) per rank past the first.")
        public ValidatedDouble pacifyRadiusPerRank = new ValidatedDouble(2.0, 256.0, 0.0);
        @Comment("Pacify: duration in ticks at rank 1 (20 ticks = 1 second).")
        public ValidatedInt pacifyDurationTicksBase = new ValidatedInt(100, 1_000_000, 1);
        @Comment("Pacify: extra duration in ticks per rank past the first.")
        public ValidatedInt pacifyDurationTicksPerRank = new ValidatedInt(60, 1_000_000, 0);
        @Comment("Reputation: daily restock cap for villagers near a holder (vanilla is 2).")
        public ValidatedInt reputationRestockLimit = new ValidatedInt(5, 100, 1);
        @Comment("Reputation: how close (blocks) a holder must be for a villager to restock eagerly.")
        public ValidatedDouble reputationNearbyRadius = new ValidatedDouble(16.0, 256.0, 1.0);
        @Comment("Wandering Eye: ticks between per-holder wandering-trader spawn rolls (2400 = 2 minutes).")
        public ValidatedInt wanderingEyeSpawnIntervalTicks = new ValidatedInt(2400, 1000000, 1);
        @Comment("Wandering Eye: chance (0-1) per roll to spawn a wandering trader near a holder.")
        public ValidatedDouble wanderingEyeSpawnChance = new ValidatedDouble(0.10, 1.0, 0.0);
    }

    /** XP from tilling, planting, and harvesting fully-grown crops. */
    public static class Herbalism extends Skill {
        @Comment("XP per block tilled with a hoe (dirt/grass/path -> farmland, etc.).")
        public ValidatedDouble xpPerTill = new ValidatedDouble(1.0, 1_000.0, 0.0);

        @Comment("XP per crop/seed planted by the player. Only counts seed-style placements (BushBlock and crop-tagged blocks); generic block placement does not award XP.")
        public ValidatedDouble xpPerPlant = new ValidatedDouble(2.0, 1_000.0, 0.0);

        @Comment("XP per harvested crop. Awarded only when the broken block is fully grown; partial-growth breaks give nothing, so misclicks aren't free XP.")
        public ValidatedDouble xpPerHarvest = new ValidatedDouble(5.0, 1_000.0, 0.0);

        @Comment("XP per successful bone meal use (growing a crop, sapling, grass, etc.). Kept small since bone meal is cheap; 0 disables.")
        public ValidatedDouble xpPerBonemeal = new ValidatedDouble(0.2, 1_000.0, 0.0);

        @Comment("Mycologist (rank 3): chance (0-1) on breaking a fungal block (mushroom, nether fungus, huge mushroom, nether wart) to drop an extra yield. 0 disables the drop bonus.")
        public ValidatedDouble mycologistFungalDropChance = new ValidatedDouble(0.5, 1.0, 0.0);
        @Comment("Mycologist (rank 3): radius (blocks) of the mycelium spread when a holder bonemeals a mushroom; converts exposed dirt-like blocks to mycelium. 0 disables the spread.")
        public ValidatedInt mycologistMyceliumSpreadRadius = new ValidatedInt(2, 16, 0);
        @Comment("Global behavior change (needed for Mycologist rank 1): when true, an already-placed mushroom on a solid block is no longer broken by a neighbor update just because it's too bright, so mushrooms placed off nylium persist. Set false to keep strict vanilla mushroom survival (the rank-1 placement still works, but those mushrooms can pop off in daylight).")
        public ValidatedBoolean mushroomsPersistInLight = new ValidatedBoolean(true);

        @Comment("Rupee Farmer loot pool: item ids, one of which drops when the perk procs on breaking foliage (grass/leaves). Edit freely.")
        public ValidatedList<String> rupeeFarmerLoot = new ValidatedString("").toList(List.of(
                "minecraft:emerald",
                "minecraft:diamond",
                "minecraft:lapis_lazuli",
                "minecraft:amethyst_shard",
                "minecraft:gold_ingot"
        ));

        @Comment("Toxin Harvest loot pool: alchemy-reagent item ids, one of which drops when the perk procs on a harvest.")
        public ValidatedList<String> toxinHarvestLoot = new ValidatedString("").toList(List.of(
                "minecraft:spider_eye",
                "minecraft:glowstone_dust",
                "minecraft:redstone",
                "minecraft:sugar",
                "minecraft:fermented_spider_eye",
                "minecraft:blaze_powder"
        ));

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Green Thumb: auto-bonemeal chance added per Herbalism level after planting a crop (0.01 = +1%/level, clamped to 1).")
        public ValidatedDouble greenThumbChancePerLevel = new ValidatedDouble(0.01, 1.0, 0.0);
        @Comment("Green Thumb: growth stages advanced per perk rank when the auto-bonemeal procs.")
        public ValidatedInt greenThumbStagesPerRank = new ValidatedInt(1, 7, 0);
        @Comment("Cultivation: extra crop yield per rank (chance 0-1).")
        public ValidatedDouble extraCropYieldPerRank = new ValidatedDouble(0.25, 1.0, 0.0);
        @Comment("Rupee Farmer: chance (0-1) on breaking foliage to drop a valuable item.")
        public ValidatedDouble rupeeFarmerChance = new ValidatedDouble(0.05, 1.0, 0.0);
        @Comment("Toxin Harvest: chance (0-1) on a harvest to drop an alchemy reagent.")
        public ValidatedDouble toxinHarvestChance = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Gardener's Infusion: hunger (nutrition) multiplier baked into food you craft/cook, all ranks. 1.5 = +50%.")
        public ValidatedDouble gardenersInfusionHungerMultiplier = new ValidatedDouble(1.5, 10.0, 1.0);
        @Comment("Gardener's Infusion: saturation multiplier baked into food, rank 2+. 1.5 = +50%.")
        public ValidatedDouble gardenersInfusionSaturationMultiplier = new ValidatedDouble(1.5, 10.0, 1.0);
        @Comment("Gardener's Infusion: positive-effect duration multiplier baked into food, rank 3. 2.0 = doubled.")
        public ValidatedDouble gardenersInfusionEffectDurationMultiplier = new ValidatedDouble(2.0, 10.0, 1.0);
        @Comment("Bountiful Harvest (active): base radius (blocks) of the grow/harvest/replant sweep.")
        public ValidatedDouble bountifulHarvestRadiusBase = new ValidatedDouble(3.0, 64.0, 1.0);
        @Comment("Bountiful Harvest (active): added radius (blocks) per perk rank (base 3 + 2/rank = 3 -> 5 -> 7).")
        public ValidatedDouble bountifulHarvestRadiusPerRank = new ValidatedDouble(2.0, 64.0, 0.0);
        @Comment("Bountiful Harvest (active): cooldown in ticks (20 = 1s).")
        public ValidatedInt bountifulHarvestCooldownTicks = new ValidatedInt(600, 72_000, 0);
        @Comment("Bountiful Harvest (active): stamina cost per activation.")
        public ValidatedDouble bountifulHarvestStaminaCost = new ValidatedDouble(20.0, 1_000.0, 0.0);
    }

    /** XP from enchanting tables, grindstone disenchants, and anvil combines. */
    public static class Enchanting extends Skill {
        @Comment("XP per enchantment applied at the table, summed over the whole roll. 'l' = applied level (Sharpness V -> 5), 'w' = rarity weight (common 10 .. very-rare 1; rarer = lower), 't' = 1 for treasure enchantments (Mending, Soul Speed, ...) else 0. Default rewards rarer, treasure, and higher-level enchants.")
        public ValidatedExpression xpPerEnchant = new ValidatedExpression("l * (11 - w) * (1 + t)", Set.of('l', 'w', 't'));

        @Comment("XP per grindstone disenchant. 'x' = experience the grindstone awards from removed enchantments. Set to 0 to disable grindstone XP.")
        public ValidatedExpression xpPerGrindstone = new ValidatedExpression("0.5 * x", Set.of('x'));

        @Comment("XP per anvil result taken. 'c' = level cost of the operation (anvil's level-cost number). Includes repair, rename, and combine; pack can split via cost thresholds in the formula.")
        public ValidatedExpression xpPerAnvil = new ValidatedExpression("c", Set.of('c'));

        @Comment("Abundance perk: per-rank chance (0-1) to roll one extra compatible enchant. The perk gives 'rank' independent chances, so at max rank (3) the extra count is binomial: 1-2 extra most likely, 0 or 3 rarer. Set 0 to neutralize the perk's effect.")
        public ValidatedDouble abundanceChance = new ValidatedDouble(0.55, 1.0, 0.0);

        @Comment("Essence Hoarder: Combat-Attributes regen added per enchantment on worn/held gear, applied to each unlocked resource (mana at rank 1, +stamina at rank 2, +health at rank 3).")
        public ValidatedDouble essenceHoarderRegenPerEnchant = new ValidatedDouble(0.1, 100.0, 0.0);

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Prodigy: enchanting level-cost discount per rank (fraction).")
        public ValidatedDouble prodigyLevelDiscountPerRank = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Abundance: extra-enchant roll trials per rank, each rolled at abundanceChance (floored to a whole number).")
        public ValidatedDouble abundanceTrialsPerRank = new ValidatedDouble(1.0, 10.0, 0.0);
        @Comment("Arcane Insight: extra enchantment clues revealed per rank (floored; 3 or more reveals all).")
        public ValidatedDouble arcaneInsightRevealPerRank = new ValidatedDouble(1.0, 10.0, 0.0);
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
     *  so each catch is classified by config; items not in either list fall through to junk. */
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

        // --- Perk effect magnitudes (baked into the skill definition at registry-freeze; restart to apply) ---
        @Comment("Patient Angler: bonus bite speed (fraction faster).")
        public ValidatedDouble biteSpeedBonus = new ValidatedDouble(0.30, 10.0, 0.0);
        @Comment("Patient Angler: bonus lure range (blocks).")
        public ValidatedDouble lureRangeBonus = new ValidatedDouble(1.0, 64.0, 0.0);
        @Comment("Fortune's Catch: bonus treasure chance (0-1) per rank.")
        public ValidatedDouble treasureBonusPerRank = new ValidatedDouble(0.10, 1.0, 0.0);
        @Comment("Fortune's Catch: flat luck bonus while fishing.")
        public ValidatedDouble luckBonus = new ValidatedDouble(1.0, 100.0, 0.0);
        @Comment("Big Catch: chance (0-1) to reel a double catch.")
        public ValidatedDouble doubleCatchChance = new ValidatedDouble(0.20, 1.0, 0.0);
        @Comment("Enchanted Catch: chance (0-1) for a catch to come pre-enchanted.")
        public ValidatedDouble enchantedCatchChance = new ValidatedDouble(0.15, 1.0, 0.0);
        @Comment("Enchanted Catch: enchantment power level applied to a pre-enchanted catch (like a table level).")
        public ValidatedInt enchantedCatchLevel = new ValidatedInt(25, 100, 1);
        @Comment("Patient Angler: reference lure-wait ticks the bite-speed fraction reduces (vanilla average wait ~350).")
        public ValidatedInt biteSpeedReferenceTicks = new ValidatedInt(350, 6000, 1);
        @Comment("Patient Angler: lure-wait ticks removed per block of lure range.")
        public ValidatedInt lureRangeTicksPerBlock = new ValidatedInt(20, 600, 0);
        @Comment("Harpoon: velocity strength reeling a trident-struck mob toward you.")
        public ValidatedDouble harpoonReelStrength = new ValidatedDouble(0.6, 10.0, 0.0);
        @Comment("Harpoon: flat bonus ranged damage on the trident/harpoon throw.")
        public ValidatedDouble harpoonRangedDamage = new ValidatedDouble(2.0, 1000.0, 0.0);
        @Comment("Trident Master: bonus trident return/loyalty speed.")
        public ValidatedDouble tridentReturnSpeed = new ValidatedDouble(0.5, 10.0, 0.0);
        @Comment("Trident Master: bonus trident throw velocity (fraction of base).")
        public ValidatedDouble tridentVelocityBonus = new ValidatedDouble(0.15, 10.0, 0.0);
    }
}
