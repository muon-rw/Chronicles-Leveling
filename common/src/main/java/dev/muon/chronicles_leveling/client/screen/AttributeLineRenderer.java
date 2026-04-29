package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.combat_attributes.attribute.DiminishingAttribute;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Renders attribute tooltip lines (base values and modifiers) using the loader's
 * native formatter where available.
 *
 * <p>Cascade for both methods:
 * <ol>
 *   <li><b>Platform</b> via {@link Services#PLATFORM} — NeoForge routes through
 *       {@code IAttributeExtension.toComponent} / {@code toBaseComponent}, so
 *       {@code PercentageAttribute} and modded {@code Attribute} subclasses pick
 *       their own format. Fabric routes through Dynamic Tooltips when loaded
 *       (sentiment overrides + percent rules), and returns empty otherwise.</li>
 *   <li><b>Vanilla fallback</b> — the standard {@code attribute.modifier.{plus|take}.{op}}
 *       translation keys, with {@code ADD_MULTIPLIED_*} amounts pre-scaled by 100.</li>
 * </ol>
 */
public final class AttributeLineRenderer {

    /** Mirrors NeoForge / vanilla's "#.##" pattern, used by the vanilla fallback only. */
    private static final DecimalFormat FORMAT =
            new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    /** Sort order for value-hover breakdowns: by op id, then larger absolute values first, then modifier id. */
    public static final Comparator<AttributeModifier> MODIFIER_ORDER =
            Comparator.comparing(AttributeModifier::operation)
                    .thenComparing((AttributeModifier m) -> -Math.abs(m.amount()))
                    .thenComparing(AttributeModifier::id);

    private AttributeLineRenderer() {}

    /**
     * Build the standard value-hover breakdown: one base line (omitted when zero)
     * followed by one line per modifier (zero-amount filtered, sorted by
     * {@link #MODIFIER_ORDER}). Each line goes through the loader-aware formatter
     * so DT / NeoForge-native / vanilla all reach the right outcome.
     *
     * <p>If the attribute uses Combat Attributes' diminishing-stacking path, a
     * trailing gray line summarising the per-source soft cap is appended so players
     * can see the ceiling without reading the config.
     */
    public static List<Component> valueBreakdown(Holder<Attribute> holder, double baseValue,
                                                 Collection<AttributeModifier> modifiers,
                                                 Optional<Double> percentScale) {
        List<Component> lines = new ArrayList<>();
        if (baseValue != 0) {
            lines.add(baseValueComponent(holder, baseValue));
        }
        List<AttributeModifier> sorted = new ArrayList<>(modifiers);
        sorted.sort(MODIFIER_ORDER);
        for (AttributeModifier modifier : sorted) {
            if (modifier.amount() == 0) continue;
            lines.add(modifierComponent(holder, modifier));
        }
        lines.addAll(diminishingNotice(holder, percentScale));
        return lines;
    }

    public static Component modifierComponent(Holder<Attribute> holder, AttributeModifier modifier) {
        return Services.PLATFORM.modifierComponent(holder, modifier)
                .orElseGet(() -> vanillaModifierFallback(holder.value(), modifier));
    }

    public static Component baseValueComponent(Holder<Attribute> holder, double value) {
        return Services.PLATFORM.baseValueComponent(holder, value)
                .orElseGet(() -> vanillaBaseValueFallback(holder.value(), value));
    }

    private static Component vanillaBaseValueFallback(Attribute attribute, double value) {
        return Component.translatable("attribute.modifier.equals.0",
                FORMAT.format(value),
                Component.translatable(attribute.getDescriptionId()))
                .withStyle(ChatFormatting.DARK_GREEN);
    }

    /**
     * Builds the three-line "diminishing returns" footer for any attribute backed by
     * a Combat Attributes {@link DiminishingAttribute}: a header, a per-source soft
     * cap line, and (probabilistic mode only) a stacking note. Probabilistic attributes
     * combine sources via {@code 1 - Π(1-p)}, so two 40% sources give 64% rather than
     * 80%; soft-cap attributes sum across operation slots, so the third line would lie
     * about them and is skipped.
     *
     * <p>Picks percent vs flat formatting from the same {@code percentScale} the row
     * uses to display its current value, so the unit shown in the footer matches the
     * unit in the value column above it. Probabilistic caps are absolute probabilities
     * (no leading "+"); soft-cap caps are additive bonuses above base, so they get a
     * "+" prefix to make the additive nature legible.
     */
    private static List<Component> diminishingNotice(Holder<Attribute> holder, Optional<Double> percentScale) {
        if (!(holder.value() instanceof DiminishingAttribute dim)) return List.of();
        double cap = dim.softCap();
        boolean probabilistic = dim.isProbabilistic();
        String prefix = probabilistic ? "" : "+";
        String formatted = percentScale.isPresent()
                ? prefix + FORMAT.format(cap * percentScale.get()) + "%"
                : prefix + FORMAT.format(cap);
        List<Component> footer = new ArrayList<>(3);
        footer.add(Component.translatable("chronicles_leveling.attributes.diminishing_one")
                .withStyle(ChatFormatting.WHITE));
        footer.add(Component.translatable("chronicles_leveling.attributes.diminishing_two", formatted)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (probabilistic) {
            footer.add(Component.translatable("chronicles_leveling.attributes.diminishing_three")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return footer;
    }

    private static Component vanillaModifierFallback(Attribute attribute, AttributeModifier modifier) {
        double amount = modifier.amount();
        boolean positive = amount > 0;
        String key = "attribute.modifier." + (positive ? "plus." : "take.") + modifier.operation().id();
        // ADD_MULTIPLIED_* keys already supply the "%" suffix; ADD_VALUE keys do not.
        double display = modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                ? Math.abs(amount)
                : Math.abs(amount) * 100.0;
        return Component.translatable(key,
                FORMAT.format(display),
                Component.translatable(attribute.getDescriptionId()))
                .withStyle(attribute.getStyle(positive));
    }
}
