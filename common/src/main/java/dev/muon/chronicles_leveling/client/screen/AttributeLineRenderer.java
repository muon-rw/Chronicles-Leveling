package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.combat_attributes.attribute.DiminishingAttribute;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
     * Build the standard value-hover breakdown.
     *
     * <p>When the attribute has any non-zero modifiers, the layout mirrors the
     * shift-expanded NeoForge / Dynamic-Tooltips merged section: a gold "total"
     * header (the actual diminished value the player sees in the row) followed
     * by the green base line and each modifier indented under a {@code  ┇ }
     * gray prefix. With no modifiers, a single green base line is emitted.
     *
     * <p>Percent attributes diverge from the loader convention: NeoForge / DT
     * leave their base line at the raw decimal because their merged-base path
     * only triggers for non-percent attributes (attack damage / speed / reach).
     * Our value column already renders percent attributes scaled, so we scale
     * both the gold total and green base to keep the tooltip consistent with
     * the row above it. The per-modifier child lines flow through
     * {@link #modifierComponent} unchanged — those already percent-scale
     * natively.
     *
     * <p>If the attribute uses Combat Attributes' diminishing-stacking path, a
     * trailing gray line summarising the per-source soft cap is appended so
     * players can see the ceiling without reading the config.
     */
    public static List<Component> valueBreakdown(Holder<Attribute> holder, double totalValue, double baseValue,
                                                 Collection<AttributeModifier> modifiers,
                                                 Optional<Double> percentScale) {
        List<AttributeModifier> sorted = new ArrayList<>(modifiers);
        sorted.removeIf(m -> m.amount() == 0);
        sorted.sort(MODIFIER_ORDER);

        List<Component> lines = new ArrayList<>();
        if (sorted.isEmpty()) {
            if (baseValue != 0) {
                lines.add(baseValueComponent(holder, baseValue, percentScale));
            }
        } else {
            lines.add(totalValueComponent(holder, totalValue, percentScale));
            if (baseValue != 0) {
                lines.add(indent(baseValueComponent(holder, baseValue, percentScale)));
            }
            for (AttributeModifier modifier : sorted) {
                lines.add(indent(modifierComponent(holder, modifier)));
            }
        }
        lines.addAll(diminishingNotice(holder, percentScale));
        return lines;
    }

    public static Component modifierComponent(Holder<Attribute> holder, AttributeModifier modifier) {
        return Services.PLATFORM.modifierComponent(holder, modifier)
                .orElseGet(() -> vanillaModifierFallback(holder.value(), modifier));
    }

    public static Component baseValueComponent(Holder<Attribute> holder, double value, Optional<Double> percentScale) {
        if (percentScale.isPresent()) {
            return percentEqualsComponent(holder, value, percentScale.get(), ChatFormatting.DARK_GREEN);
        }
        return Services.PLATFORM.baseValueComponent(holder, value)
                .orElseGet(() -> vanillaBaseValueFallback(holder.value(), value));
    }

    /**
     * Gold "total" header used above an indented modifier list. NeoForge and DT
     * both build the same {@code attribute.modifier.equals.0} translatable for
     * their merged base line, just colored gold instead of dark green; we either
     * do the same recolor or build a percent-aware equivalent when scaling.
     */
    public static Component totalValueComponent(Holder<Attribute> holder, double value, Optional<Double> percentScale) {
        if (percentScale.isPresent()) {
            return percentEqualsComponent(holder, value, percentScale.get(), ChatFormatting.GOLD);
        }
        Component base = Services.PLATFORM.baseValueComponent(holder, value)
                .orElseGet(() -> vanillaBaseValueFallback(holder.value(), value));
        // Loader formatters bake DARK_GREEN into the returned component; GOLD is a color
        // format so applyFormats overwrites the color while preserving any other flags.
        return base.copy().withStyle(ChatFormatting.GOLD);
    }

    private static Component percentEqualsComponent(Holder<Attribute> holder, double value, double scale, ChatFormatting color) {
        return Component.translatable("attribute.modifier.equals.0",
                        FORMAT.format(value * scale) + "%",
                        Component.translatable(holder.value().getDescriptionId()))
                .withStyle(color);
    }

    /** Matches NeoForge / DT's expanded-list prefix exactly (U+2507 between two spaces). */
    private static MutableComponent indent(Component line) {
        return Component.literal(" ┇ ").withStyle(ChatFormatting.GRAY).append(line);
    }

    private static Component vanillaBaseValueFallback(Attribute attribute, double value) {
        return Component.translatable("attribute.modifier.equals.0",
                FORMAT.format(value),
                Component.translatable(attribute.getDescriptionId()))
                .withStyle(ChatFormatting.DARK_GREEN);
    }

    /**
     * Builds the "diminishing returns" footer for any attribute backed by a Combat
     * Attributes {@link DiminishingAttribute}: a header, a per-source soft cap line,
     * and (for non-additive modes) a stacking note. Probabilistic attributes combine
     * sources via {@code 1 - Π(1-p)}; multiplicative attributes combine via
     * {@code Π(1 - softCapped(-x))}, so two 30% reductions give 51% off rather than
     * 60%. Soft-cap attributes sum across operation slots, so the third line would lie
     * about them and is skipped.
     *
     * <p>Picks percent vs flat formatting from the same {@code percentScale} the row
     * uses to display its current value, so the unit shown in the footer matches the
     * unit in the value column above it. Cap-prefix conventions:
     * <ul>
     *   <li>SOFT_CAP — additive bonus above base, "{@code +}" prefix.</li>
     *   <li>PROBABILISTIC — absolute probability, no prefix.</li>
     *   <li>MULTIPLICATIVE — magnitude of the buff side; for NEGATIVE-sentiment attrs
     *       (e.g. mana_cost) buffs are reductions, so the cap renders with a
     *       "{@code -}" prefix to read as "max savings per source".</li>
     * </ul>
     */
    private static List<Component> diminishingNotice(Holder<Attribute> holder, Optional<Double> percentScale) {
        if (!(holder.value() instanceof DiminishingAttribute dim)) return List.of();
        double cap = dim.softCap();
        String prefix = capPrefix(holder.value(), dim);
        String formatted = percentScale.isPresent()
                ? prefix + FORMAT.format(cap * percentScale.get()) + "%"
                : prefix + FORMAT.format(cap);
        boolean nonAdditive = dim.isProbabilistic() || dim.isMultiplicative();
        List<Component> footer = new ArrayList<>(3);
        footer.add(Component.translatable("chronicles_leveling.attributes.diminishing_one")
                .withStyle(ChatFormatting.WHITE));
        footer.add(Component.translatable("chronicles_leveling.attributes.diminishing_two", formatted)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (nonAdditive) {
            footer.add(Component.translatable("chronicles_leveling.attributes.diminishing_three")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return footer;
    }

    private static String capPrefix(Attribute attribute, DiminishingAttribute dim) {
        if (dim.isProbabilistic()) return "";
        if (dim.isMultiplicative()) return isNegativeSentiment(attribute) ? "-" : "+";
        return "+";
    }

    /**
     * Probes attribute sentiment via the public {@link Attribute#getStyle} API rather
     * than reflecting on the private {@code sentiment} field. POSITIVE → BLUE on
     * increase, NEGATIVE → RED on increase, NEUTRAL → GRAY — so {@code RED} on
     * {@code getStyle(true)} uniquely identifies NEGATIVE.
     */
    private static boolean isNegativeSentiment(Attribute attribute) {
        return attribute.getStyle(true) == ChatFormatting.RED;
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
