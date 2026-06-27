package dev.muon.chronicles_leveling.component;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponentsFabric {

    public static final DataComponentType<BrewPotency> BREW_POTENCY = register("brew_potency",
            DataComponentType.<BrewPotency>builder()
                    .persistent(BrewPotency.CODEC)
                    .networkSynchronized(BrewPotency.STREAM_CODEC)
                    .build());

    public static final DataComponentType<GardenersInfusion> GARDENERS_INFUSION = register("gardeners_infusion",
            DataComponentType.<GardenersInfusion>builder()
                    .persistent(GardenersInfusion.CODEC)
                    .networkSynchronized(GardenersInfusion.STREAM_CODEC)
                    .build());

    private ModComponentsFabric() {}

    private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ChroniclesLeveling.id(path), type);
    }

    public static void init() {
        ModComponents.BREW_POTENCY = BREW_POTENCY;
        ModComponents.GARDENERS_INFUSION = GARDENERS_INFUSION;
    }
}
