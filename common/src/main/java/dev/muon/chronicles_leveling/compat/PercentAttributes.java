package dev.muon.chronicles_leveling.compat;

import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.Optional;

/**
 * Decides whether a given attribute should display as a percentage on the
 * Attributes screen, and if so, the multiplier used to convert its raw value
 * into the displayed percent (e.g. {@code 0.05} × 100 → {@code "5%"}).
 *
 * <p>Lookup order:
 * <ol>
 *   <li>Dynamic-Tooltips runtime registry, if loaded.</li>
 *   <li>Combat-Attributes' own metadata, read directly — the NeoForge build of
 *       Combat-Attributes never hands its attributes to DT, so we can't rely
 *       on DT alone.</li>
 *   <li>NeoForge's {@code PercentageAttribute}, via the platform helper —
 *       covers vanilla NeoForge percent attributes (knockback resistance etc.).</li>
 * </ol>
 *
 * <p>Returns empty when no source recognizes the attribute, in which case the
 * screen renders a plain numeric value.
 */
public final class PercentAttributes {

    private PercentAttributes() {}

    public static Optional<Double> scaleFor(Identifier id, Holder<Attribute> holder) {
        Optional<Double> dt = DynamicTooltipsCompat.scaleFor(id);
        if (dt.isPresent()) return dt;

        Optional<Double> ca = CombatAttributesCompat.scaleFor(id);
        if (ca.isPresent()) return ca;

        return Services.PLATFORM.percentScaleForAttribute(holder);
    }
}
