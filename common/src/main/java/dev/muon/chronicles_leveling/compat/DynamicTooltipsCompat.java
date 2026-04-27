package dev.muon.chronicles_leveling.compat;

import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Lazy bridge to {@link dev.muon.dynamictooltips.api.DynamicTooltipsAPI} for
 * percent-attribute scale lookups.
 *
 * <p>JVM class loading is lazy: the inner {@link Lookup} class only resolves
 * Dynamic-Tooltips classes the first time {@link #scaleFor(Identifier)} reaches
 * the {@code LOADED} branch, so {@code NoClassDefFoundError} can't fire when
 * the mod isn't on the classpath.
 */
public final class DynamicTooltipsCompat {

    // Mod ID has no underscore — matches Combat-Attributes' own check in CombatAttributesFabricClient.
    private static final String MOD_ID = "dynamictooltips";
    private static final boolean LOADED = Services.PLATFORM.isModLoaded(MOD_ID);

    private DynamicTooltipsCompat() {}

    public static Optional<Double> scaleFor(Identifier id) {
        return LOADED ? Lookup.scaleFor(id) : Optional.empty();
    }

    private static final class Lookup {
        static Optional<Double> scaleFor(Identifier id) {
            dev.muon.dynamictooltips.api.DynamicTooltipsAPI.PercentRule rule =
                    dev.muon.dynamictooltips.api.DynamicTooltipsAPI.percentAttributes().get(id);
            if (rule == null) return Optional.empty();
            double s = rule.scaleFactor();
            return s > 0 ? Optional.of(s) : Optional.empty();
        }
    }
}
