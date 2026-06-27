package dev.muon.chronicles_leveling.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.DashAbility;
import dev.muon.chronicles_leveling.skill.catalog.AcrobaticsSkill;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import dev.muon.chronicles_leveling.skill.catalog.ArcherySkill;
import dev.muon.chronicles_leveling.skill.catalog.DefenseSkill;
import dev.muon.chronicles_leveling.skill.catalog.EnchantingSkill;
import dev.muon.chronicles_leveling.skill.catalog.FishingSkill;
import dev.muon.chronicles_leveling.skill.catalog.HerbalismSkill;
import dev.muon.chronicles_leveling.skill.catalog.MiningSkill;
import dev.muon.chronicles_leveling.skill.catalog.SpeechSkill;
import dev.muon.chronicles_leveling.skill.catalog.WeaponrySkill;
import dev.muon.chronicles_leveling.skill.perk.AttributeEffect;
import dev.muon.chronicles_leveling.skill.perk.CapabilityGrant;
import dev.muon.chronicles_leveling.skill.perk.PerkEffect;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;

/**
 * Paints the skill-tree perk node tooltip: a translucent, centered, narrow-wrapped box with a
 * separator under the title, and inline dynamic values that read the live current value with a
 * dimmed {@code current -> next} preview. Wrapping happens here ({@link Font#split}) so Fabric and
 * NeoForge render identically; vanilla's component-tooltip path leaves long lines unwrapped on Fabric.
 *
 * <p>Each perk's values are keyed by {@code owningSkill + "/" + perkId} and filled into the
 * description's {@code %1$s, %2$s, ...} slots (in registration order). A value reads the perk's
 * actual effect: a rank-scaled {@link CapabilityGrant} Double, or an {@link AttributeEffect}'s
 * level-scaled magnitude, or any rank-keyed override. Format is per-argument, so one perk can mix
 * percentages and flat numbers.
 */
public final class PerkTooltipRenderer {

    private PerkTooltipRenderer() {}

    private static final int WRAP_WIDTH = 150;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int SEP_H = 1;
    private static final int SEP_GAP = 2;
    private static final int CURSOR_OFFSET = 12;
    private static final int BG_COLOR = 0xC8140D08;        // ~78% opaque warm near-black
    private static final int BORDER_COLOR = 0xFF8B7355;
    private static final int SEP_COLOR = 0xFF8B7355;
    private static final int TEXT_FALLBACK = 0xFFFFFFFF;   // each row carries its own style; this is the no-style color
    private static final String ARROW = " → ";

    @FunctionalInterface
    public interface PerkValueProvider {
        double valueAt(SkillPerk perk, int rank, int level);

        /** Reads the rank-scaled value the perk actually grants for {@code cap} at the given rank. */
        static PerkValueProvider capability(SkillCapability<Double> cap) {
            return (perk, rank, _) -> {
                for (PerkEffect effect : perk.effectsAtRank(rank)) {
                    if (effect instanceof CapabilityGrant<?>(var capability, var value) && capability == cap) {
                        return ((Number) value).doubleValue();
                    }
                }
                return 0.0;
            };
        }

        /** Override source: a config value or any rank-keyed function instead of the granted effect. */
        static PerkValueProvider of(IntToDoubleFunction byRank) {
            return (_, rank, _) -> byRank.applyAsDouble(rank);
        }

        /** Reads a capability's rank-scaled value then maps it into a derived value (e.g. a threshold from a bonus). */
        static PerkValueProvider mapped(SkillCapability<Double> cap, DoubleUnaryOperator fn) {
            PerkValueProvider source = capability(cap);
            return (perk, rank, level) -> fn.applyAsDouble(source.valueAt(perk, rank, level));
        }
    }

    /** Renders one description argument: the formatted current value for (perk, rank, level). */
    @FunctionalInterface
    public interface PerkValue {
        String render(SkillPerk perk, int rank, int level);
    }

    public static PerkValue percent(PerkValueProvider provider) {
        return (perk, rank, level) -> number(provider.valueAt(perk, rank, level) * 100.0) + "%";
    }

    public static PerkValue flat(PerkValueProvider provider) {
        return (perk, rank, level) -> number(provider.valueAt(perk, rank, level));
    }

    /** A non-numeric, rank-keyed text value (e.g. a growing list of resources); the current -> next preview still applies. */
    public static PerkValue text(IntFunction<String> byRank) {
        return (_, rank, _) -> byRank.apply(rank);
    }

    /**
     * A duration formatted as seconds (e.g. "5s") from a tick provider. A distinct type so {@link #inlinesDuration}
     * can detect that a perk surfaces its own duration, letting the ability-stat footnote defer to the description.
     */
    public static PerkValue duration(PerkValueProvider tickProvider) {
        return new DurationValue(tickProvider);
    }

    private record DurationValue(PerkValueProvider tickProvider) implements PerkValue {
        @Override
        public String render(SkillPerk perk, int rank, int level) {
            return seconds((int) Math.round(tickProvider.valueAt(perk, rank, level)));
        }
    }

    private static String seconds(int ticks) {
        return number(ticks / 20.0) + "s";
    }

    /**
     * A level-scaled attribute contribution ({@code magnitude.eval(level) * rank}), formatted like the
     * Attributes screen: a multiplied-base/total operation, or a registered percentage attribute, renders as a
     * percent (NeoForge {@code PercentageAttribute} natively, Fabric via Dynamic-Tooltips); everything else is flat.
     */
    public static PerkValue attribute(Identifier attribute) {
        return (perk, rank, level) -> {
            for (PerkEffect effect : perk.effectsAtRank(rank)) {
                if (effect instanceof AttributeEffect(var id, var op, var magnitude) && id.equals(attribute)) {
                    double raw = magnitude.eval(level) * rank;
                    if (op == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                            || op == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                        return number(raw * 100.0) + "%";
                    }
                    return percentScale(attribute)
                            .map(scale -> number(raw * scale) + "%")
                            .orElseGet(() -> number(raw));
                }
            }
            return number(0.0);
        };
    }

    private static Optional<Double> percentScale(Identifier attribute) {
        ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attribute);
        Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(key);
        return holder.flatMap(Services.PLATFORM::percentScaleForAttribute);
    }

    /** key = owningSkill + "/" + perkId; list order matches the description's %1$s, %2$s, ... slots. */
    private static final Map<String, List<PerkValue>> VALUES = new HashMap<>();

    static {
        bootstrapAlchemy();
        bootstrapWeaponry();
        bootstrapArchery();
        bootstrapDefense();
        bootstrapAcrobatics();
        bootstrapMining();
        bootstrapHerbalism();
        bootstrapFishing();
        bootstrapEnchanting();
        bootstrapSpeech();
    }

    public static void register(String skillId, String perkId, PerkValue... values) {
        VALUES.put(skillId + "/" + perkId, List.of(values));
    }

    /** Whether the perk inlines a duration in its description, so the ability-stat footnote can omit the duration line. */
    public static boolean inlinesDuration(String skillId, String perkId) {
        List<PerkValue> values = VALUES.get(skillId + "/" + perkId);
        if (values != null) {
            for (PerkValue value : values) {
                if (value instanceof DurationValue) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void bootstrapAlchemy() {
        String s = Skills.ALCHEMY;
        register(s, "experimental_elixir", flat(PerkValueProvider.of(rank -> rank)));
        register(s, "catalysis",
                percent(PerkValueProvider.capability(AlchemySkill.BREW_SPEED)),
                percent(PerkValueProvider.capability(AlchemySkill.FUEL_SAVE)));
        register(s, "lingering_touch", percent(PerkValueProvider.capability(AlchemySkill.LINGERING_TOUCH)));
        register(s, "master_brewer", percent(PerkValueProvider.capability(AlchemySkill.EXTRA_BREW_CHANCE)));
        register(s, "quick_quaff", percent(PerkValueProvider.capability(AlchemySkill.QUICK_QUAFF)));
        register(s, "empowered_splash", percent(PerkValueProvider.capability(AlchemySkill.EMPOWERED_SPLASH)));
        register(s, "iron_stomach", percent(PerkValueProvider.capability(AlchemySkill.IRON_STOMACH)));
    }

    private static void bootstrapWeaponry() {
        String s = Skills.WEAPONRY;
        register(s, "slashing_focus", percent(PerkValueProvider.capability(WeaponrySkill.SLASHING_DAMAGE)));
        register(s, "rend",
                percent(PerkValueProvider.capability(WeaponrySkill.REND_CHANCE)),
                flat(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.rendBleedDamageBase.get())),
                flat(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.rendBleedDamagePerStack.get())),
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.rendBleedDurationTicks.get())));
        register(s, "quick_blade",
                percent(PerkValueProvider.capability(WeaponrySkill.QUICK_BLADE)),
                flat(PerkValueProvider.capability(WeaponrySkill.QUICK_BLADE_MAX_STACKS)));
        register(s, "riposte", percent(PerkValueProvider.capability(WeaponrySkill.RIPOSTE_CHANCE)));
        register(s, "piercing_focus", percent(PerkValueProvider.capability(WeaponrySkill.PIERCING_DAMAGE)));
        register(s, "armor_pierce", percent(PerkValueProvider.capability(WeaponrySkill.ARMOR_PIERCE)));
        register(s, "executioner",
                percent(PerkValueProvider.capability(WeaponrySkill.EXECUTIONER)),
                percent(PerkValueProvider.mapped(WeaponrySkill.EXECUTIONER, exec -> {
                    var w = Configs.SKILLS.weaponry;
                    return Math.min(w.executionerWindowMax.get(),
                            w.executionerWindowBase.get() + w.executionerWindowPerBonus.get() * exec);
                })));
        register(s, "blunt_focus", percent(PerkValueProvider.capability(WeaponrySkill.BLUNT_DAMAGE)));
        register(s, "sunder",
                percent(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.sunderArmorShred.get())),
                flat(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.sunderMaxStacks.get())));
        register(s, "concussive_blows",
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.concussiveSlownessTicks.get())));
        register(s, "seismic_slam",
                flat(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.seismicSlamDamage.get())),
                flat(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.seismicSlamRadius.get())),
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.seismicSlamSlowTicks.get())));
        register(s, "heavy_hitter", percent(PerkValueProvider.capability(WeaponrySkill.HEAVY_HITTER)));
        register(s, "bloodthirst", attribute(Identifier.fromNamespaceAndPath("combat_attributes", "lifesteal")));
        register(s, "momentum",
                percent(PerkValueProvider.capability(WeaponrySkill.MOMENTUM)),
                flat(PerkValueProvider.capability(WeaponrySkill.MOMENTUM_MAX_STACKS)));
        register(s, "masters_focus",
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.mastersFocusDurationTicks.get())),
                percent(PerkValueProvider.of(_ -> Configs.SKILLS.weaponry.mastersFocusTrueDamageFraction.get())));
    }

    private static void bootstrapArchery() {
        String s = Skills.ARCHERY;
        register(s, "strong_arm", attribute(Identifier.fromNamespaceAndPath("combat_attributes", "arrow_velocity")));
        register(s, "quick_hands", attribute(Identifier.fromNamespaceAndPath("combat_attributes", "draw_speed")));
        register(s, "far_shot", percent(PerkValueProvider.capability(ArcherySkill.FAR_SHOT_BONUS)));
        register(s, "multishot", flat(PerkValueProvider.capability(ArcherySkill.MULTISHOT_ARROWS)));
        register(s, "bullseye",
                attribute(Identifier.fromNamespaceAndPath("combat_attributes", "ranged_crit_chance")),
                attribute(Identifier.fromNamespaceAndPath("combat_attributes", "ranged_crit_damage")));
        register(s, "marksmans_eye", attribute(Identifier.fromNamespaceAndPath("combat_attributes", "accuracy")));
        register(s, "ricochet", flat(PerkValueProvider.capability(ArcherySkill.RICOCHET_COUNT)));
        register(s, "disorient",
                percent(PerkValueProvider.capability(ArcherySkill.DISORIENT_CHANCE)),
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.archery.disorientDurationTicks.get())));
        register(s, "pinning_shot",
                percent(PerkValueProvider.capability(ArcherySkill.PINNING_CHANCE)),
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.archery.pinningDurationTicks.get())));
    }

    private static void bootstrapDefense() {
        String s = Skills.DEFENSE;
        register(s, "iron_skin", attribute(Identifier.withDefaultNamespace("armor")));
        register(s, "magic_ward", attribute(Identifier.fromNamespaceAndPath("combat_attributes", "magic_defense")));
        register(s, "pain_tolerance", percent(PerkValueProvider.capability(DefenseSkill.MAX_HIT_FRACTION)));
        register(s, "last_stand",
                percent(PerkValueProvider.of(_ -> Configs.SKILLS.defense.lastStandThreshold.get())),
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.defense.lastStandDurationTicks.get())));
        register(s, "shield_master",
                flat(PerkValueProvider.capability(DefenseSkill.WIDE_BLOCK_ARC)),
                percent(PerkValueProvider.capability(DefenseSkill.SHIELD_BASH_CHANCE)));
        register(s, "retribution", percent(PerkValueProvider.capability(DefenseSkill.RETRIBUTION_REFLECT)));
    }

    private static void bootstrapAcrobatics() {
        String s = Skills.ACROBATICS;
        register(s, "feather_fall", attribute(Identifier.withDefaultNamespace("safe_fall_distance")));
        register(s, "roll", percent(PerkValueProvider.capability(AcrobaticsSkill.ROLL_REDUCTION)));
        register(s, "spring_step",
                attribute(Identifier.withDefaultNamespace("jump_strength")),
                attribute(Identifier.withDefaultNamespace("step_height")));
        register(s, "dodge", attribute(Identifier.fromNamespaceAndPath("combat_attributes", "evasion")));
        register(s, "catlike",
                attribute(Identifier.withDefaultNamespace("sneaking_speed")),
                percent(PerkValueProvider.capability(AcrobaticsSkill.REDUCED_DETECTION)));
        register(s, "momentum_vault",
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.acrobatics.momentumVaultSpeedTicks.get())));
        register(s, "dash", duration(PerkValueProvider.of(_ -> SkillRegistry.ability(DashAbility.ID).durationTicks())));
    }

    private static void bootstrapMining() {
        String s = Skills.MINING;
        register(s, "vein_sense",
                flat(PerkValueProvider.of(_ -> Configs.SKILLS.mining.veinSenseEfficiencyPerLevel.get())),
                attribute(Identifier.withDefaultNamespace("mining_efficiency")));
        register(s, "mother_lode", percent(PerkValueProvider.capability(MiningSkill.EXTRA_ORE_DROP)));
        register(s, "fortunate_strikes", flat(PerkValueProvider.capability(MiningSkill.NATURAL_FORTUNE)));
        register(s, "gem_hunter", percent(PerkValueProvider.capability(MiningSkill.RARE_ORE_BONUS)));
        register(s, "deep_diver", attribute(Identifier.withDefaultNamespace("submerged_mining_speed")));
        register(s, "sturdy_tools", percent(PerkValueProvider.capability(MiningSkill.TOOL_DURABILITY_SAVE)));
        register(s, "smelters_touch",
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.mining.smeltersTouchDurationTicks.get())));
        register(s, "superbreaker",
                duration(PerkValueProvider.of(rank -> Configs.SKILLS.mining.superbreakerDurationPerRankTicks.get() * rank)));
        register(s, "spelunker",
                duration(PerkValueProvider.of(_ -> Configs.SKILLS.mining.veinSightDurationTicks.get())));
    }

    private static void bootstrapHerbalism() {
        String s = Skills.HERBALISM;
        var h = Configs.SKILLS.herbalism;
        register(s, "green_thumb",
                percent((perk, rank, level) -> Math.min(1.0, level * h.greenThumbChancePerLevel.get())),
                flat(PerkValueProvider.of(rank -> (double) (rank * h.greenThumbStagesPerRank.get()))));
        register(s, "cultivation", percent(PerkValueProvider.capability(HerbalismSkill.EXTRA_CROP_YIELD)));
        register(s, "mycologist", text(PerkTooltipRenderer::mycologistBenefit));
        register(s, "rupee_farmer", percent(PerkValueProvider.capability(HerbalismSkill.RUPEE_FARMER)));
        register(s, "toxin_harvest", percent(PerkValueProvider.capability(HerbalismSkill.TOXIN_HARVEST)));
        register(s, "gardeners_infusion", text(PerkTooltipRenderer::gardenersInfusionBenefit));
        register(s, "bountiful_harvest",
                flat(PerkValueProvider.of(rank -> h.bountifulHarvestRadiusBase.get()
                        + h.bountifulHarvestRadiusPerRank.get() * (rank - 1))));
    }

    private static void bootstrapFishing() {
        String s = Skills.FISHING;
        register(s, "patient_angler",
                percent(PerkValueProvider.capability(FishingSkill.BITE_SPEED)),
                flat(PerkValueProvider.capability(FishingSkill.LURE_RANGE)));
        register(s, "fortunes_catch",
                attribute(Identifier.withDefaultNamespace("luck")),
                percent(PerkValueProvider.capability(FishingSkill.TREASURE_BONUS)));
        register(s, "big_catch", percent(PerkValueProvider.capability(FishingSkill.DOUBLE_CATCH)));
        register(s, "enchanted_catch", percent(PerkValueProvider.capability(FishingSkill.ENCHANTED_CATCH)));
        register(s, "harpoon", attribute(Identifier.fromNamespaceAndPath("combat_attributes", "ranged_damage")));
        register(s, "trident_master",
                attribute(Identifier.fromNamespaceAndPath("combat_attributes", "arrow_velocity")),
                percent(PerkValueProvider.capability(FishingSkill.TRIDENT_RETURN_SPEED)));
    }

    private static void bootstrapEnchanting() {
        String s = Skills.ENCHANTING;
        register(s, "prodigy", percent(PerkValueProvider.capability(EnchantingSkill.PRODIGY_LEVEL_DISCOUNT)));
        register(s, "arcane_insight", flat(PerkValueProvider.capability(EnchantingSkill.ARCANE_INSIGHT_REVEAL)));
        register(s, "abundance", flat(PerkValueProvider.capability(EnchantingSkill.ABUNDANCE_TRIALS)));
        register(s, "essence_hoarder",
                flat(PerkValueProvider.of(_ -> Configs.SKILLS.enchanting.essenceHoarderRegenPerEnchant.get())),
                text(PerkTooltipRenderer::essenceHoarderRegens));
        register(s, "transcribe",
                text(PerkTooltipRenderer::transcribeAmount),
                text(PerkTooltipRenderer::transcribeFate));
        register(s, "experimenter", text(PerkTooltipRenderer::experimenterScope));
        register(s, "essence_channeller", text(PerkTooltipRenderer::essenceChannellerScope));
    }

    /** Essence Hoarder's cumulative regen set by rank (translatable), mirroring EssenceHoarderHandler's tiered attributes. */
    private static String essenceHoarderRegens(int rank) {
        return I18n.get("chronicles_leveling.perk.enchanting.essence_hoarder.regens" + Math.max(1, Math.min(3, rank)));
    }

    private static String transcribeAmount(int rank) {
        return I18n.get(rank <= 1
                ? "chronicles_leveling.perk.enchanting.transcribe.amount_half"
                : "chronicles_leveling.perk.enchanting.transcribe.amount_all");
    }

    private static String transcribeFate(int rank) {
        return I18n.get(rank >= 3
                ? "chronicles_leveling.perk.enchanting.transcribe.fate_keep"
                : "chronicles_leveling.perk.enchanting.transcribe.fate_destroy");
    }

    /** Essence Channeller's repair scope by rank (translatable), mirroring its ability's tiers. */
    private static String essenceChannellerScope(int rank) {
        return I18n.get("chronicles_leveling.perk.enchanting.essence_channeller.scope" + Math.max(1, Math.min(3, rank)));
    }

    /** Experimenter's stations by rank (translatable): the anvil, then also the enchanting table. */
    private static String experimenterScope(int rank) {
        return I18n.get(rank <= 1
                ? "chronicles_leveling.perk.enchanting.experimenter.scope1"
                : "chronicles_leveling.perk.enchanting.experimenter.scope2");
    }

    /** Mycologist's current-rank fungal benefits (translatable, growing with rank). */
    private static String mycologistBenefit(int rank) {
        return I18n.get("chronicles_leveling.perk.herbalism.mycologist.rank" + Math.max(1, Math.min(3, rank)));
    }

    /** Gardener's Infusion's current-rank food boost (translatable; config magnitudes passed as args). */
    private static String gardenersInfusionBenefit(int rank) {
        var h = Configs.SKILLS.herbalism;
        String hunger = number((h.gardenersInfusionHungerMultiplier.get() - 1.0) * 100.0) + "%";
        if (rank <= 1) {
            return I18n.get("chronicles_leveling.perk.herbalism.gardeners_infusion.rank1", hunger);
        }
        String saturation = number((h.gardenersInfusionSaturationMultiplier.get() - 1.0) * 100.0) + "%";
        if (rank == 2) {
            return I18n.get("chronicles_leveling.perk.herbalism.gardeners_infusion.rank2", hunger, saturation);
        }
        String duration = number(h.gardenersInfusionEffectDurationMultiplier.get()) + "x";
        return I18n.get("chronicles_leveling.perk.herbalism.gardeners_infusion.rank3", hunger, saturation, duration);
    }

    private static void bootstrapSpeech() {
        String s = Skills.SPEECH;
        register(s, "haggler", percent(PerkValueProvider.capability(SpeechSkill.TRADE_DISCOUNT)));
        register(s, "master_negotiator", percent(PerkValueProvider.capability(SpeechSkill.VILLAGER_XP_BONUS)));
        register(s, "silver_tongue", percent(PerkValueProvider.capability(SpeechSkill.NO_STOCK_CONSUME)));
        register(s, "enchanted_trader", flat(PerkValueProvider.capability(SpeechSkill.ENCHANTED_TRADER)));
        register(s, "power_broker", flat(PerkValueProvider.capability(SpeechSkill.ENCHANT_LEVEL_BOOST)));
        register(s, "husbandry", percent(PerkValueProvider.capability(SpeechSkill.HUSBANDRY)));
        register(s, "pack_leader", percent(PerkValueProvider.capability(SpeechSkill.PACK_LEADER_REDUCTION)));
        register(s, "kindred_fury", percent(PerkValueProvider.capability(SpeechSkill.KINDRED_FURY)));
        register(s, "pacify",
                flat(PerkValueProvider.of(rank -> Configs.SKILLS.speech.pacifyRadiusBase.get()
                        + Configs.SKILLS.speech.pacifyRadiusPerRank.get() * (rank - 1))),
                duration(PerkValueProvider.of(rank -> Configs.SKILLS.speech.pacifyDurationTicksBase.get()
                        + Configs.SKILLS.speech.pacifyDurationTicksPerRank.get() * (rank - 1))));
    }

    /**
     * The perk's description with live values substituted, or {@code null} when the perk has no
     * {@code .desc} key. Perks without registered values render their static description.
     * {@code current} shows the rank-1 value at rank 0; the dimmed {@code -> next} preview shows only
     * for an owned, non-maxed rank when the next rank is affordable now or shift is held.
     */
    public static Component buildDescComponent(SkillPerk perk, int rank, int level, boolean affordable) {
        String descKey = "chronicles_leveling.perk." + perk.owningSkill() + "." + perk.id() + ".desc";
        if (!I18n.exists(descKey)) {
            return null;
        }
        List<PerkValue> values = VALUES.get(perk.owningSkill() + "/" + perk.id());
        if (values == null) {
            return Component.translatable(descKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        int displayRank = Math.max(rank, 1);
        boolean showNext = rank >= 1 && rank < perk.maxRank() && (affordable || shiftHeld());
        Object[] args = new Object[values.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = valueArg(perk, values.get(i), displayRank, rank, level, showNext);
        }
        return Component.translatable(descKey, args).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    private static MutableComponent valueArg(SkillPerk perk, PerkValue value,
                                             int displayRank, int rank, int level, boolean showNext) {
        String cur = value.render(perk, displayRank, level);
        MutableComponent current = Component.literal(cur).withStyle(ChatFormatting.WHITE);
        if (!showNext) {
            return current;
        }
        String next = value.render(perk, rank + 1, level);
        if (next.equals(cur)) {   // value plateaus at the next rank; show it without a no-op arrow
            return current;
        }
        return current.append(Component.literal(ARROW + next).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Whole number when within rounding of an integer, otherwise one decimal (matches the Attributes screen). */
    private static String number(double value) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05) {
            return Long.toString(rounded);
        }
        return String.format("%.1f", value);
    }

    private static boolean shiftHeld() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** A meta-list marker: render draws a horizontal rule at this position instead of a text line. */
    public static final Component SECTION_SEPARATOR = Component.literal(" chronicles_section_separator");

    /**
     * Draws the tooltip on a fresh stratum (above all screen chrome) at the cursor, clamped on-screen.
     * {@code desc} is nullable and sits directly under the title; the separator falls below the whole
     * (possibly wrapped) title block. A {@link #SECTION_SEPARATOR} entry in {@code titleAndMeta} renders
     * as a second rule (e.g. above an ability's stats), not as text.
     */
    public static void render(GuiGraphicsExtractor graphics, Font font, List<Component> titleAndMeta,
                              Component desc, int mouseX, int mouseY, int screenW, int screenH) {
        if (titleAndMeta.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> titleRows = font.split(titleAndMeta.getFirst(), WRAP_WIDTH);
        int titleCount = titleRows.size();
        List<FormattedCharSequence> rows = new ArrayList<>(titleRows);
        int sectionSepRow = -1;
        if (desc != null) {
            rows.addAll(font.split(desc, WRAP_WIDTH));
        }
        for (int i = 1; i < titleAndMeta.size(); i++) {
            Component meta = titleAndMeta.get(i);
            if (meta == SECTION_SEPARATOR) {
                sectionSepRow = rows.size();
                continue;
            }
            rows.addAll(font.split(meta, WRAP_WIDTH));
        }

        int contentW = 0;
        for (FormattedCharSequence row : rows) {
            contentW = Math.max(contentW, font.width(row));
        }
        int contentH = 0;
        for (int r = 0; r < rows.size(); r++) {
            if (r == titleCount || r == sectionSepRow) {
                contentH += SEP_GAP + SEP_H + SEP_GAP;
            } else if (r > 0) {
                contentH += LINE_GAP;
            }
            contentH += font.lineHeight;
        }

        int w = contentW + 2 * PAD_X;
        int h = contentH + 2 * PAD_Y;
        int px = mouseX + CURSOR_OFFSET;
        int py = mouseY - CURSOR_OFFSET;
        if (px + w > screenW) {
            px = Math.max(0, mouseX - CURSOR_OFFSET - w);
        }
        if (py + h > screenH) {
            py = screenH - h;
        }
        if (py < 0) {
            py = 0;
        }

        graphics.nextStratum();
        graphics.fill(px, py, px + w, py + h, BG_COLOR);
        graphics.outline(px, py, w, h, BORDER_COLOR);

        int contentLeft = px + PAD_X;
        int cy = py + PAD_Y;
        for (int r = 0; r < rows.size(); r++) {
            if (r == titleCount || r == sectionSepRow) {
                cy += SEP_GAP;
                graphics.fill(px + 1, cy, px + w - 1, cy + SEP_H, SEP_COLOR);
                cy += SEP_H + SEP_GAP;
            } else if (r > 0) {
                cy += LINE_GAP;
            }
            FormattedCharSequence row = rows.get(r);
            int rowX = contentLeft + (contentW - font.width(row)) / 2;
            graphics.text(font, row, rowX, cy, TEXT_FALLBACK, true);
            cy += font.lineHeight;
        }
    }
}
