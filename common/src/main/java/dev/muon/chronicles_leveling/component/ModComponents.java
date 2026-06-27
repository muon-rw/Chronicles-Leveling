package dev.muon.chronicles_leveling.component;

import net.minecraft.core.component.DataComponentType;

/**
 * Custom data component holders, populated by loader-specific code (NeoForge {@code DeferredRegister},
 * Fabric {@code Registry.register}) and published here in {@code init()}.
 */
public final class ModComponents {

    private ModComponents() {}

    /** Per-category amplifier add for brewed potions (see {@link BrewPotency}). */
    public static DataComponentType<BrewPotency> BREW_POTENCY;

    /** Gardener's Infusion tier baked onto crafted/cooked food (see {@link GardenersInfusion}). */
    public static DataComponentType<GardenersInfusion> GARDENERS_INFUSION;
}
