package dev.muon.chronicles_leveling.component;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponentsNeoforge {

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BrewPotency>> BREW_POTENCY =
            REGISTRY.register("brew_potency", () -> DataComponentType.<BrewPotency>builder()
                    .persistent(BrewPotency.CODEC)
                    .networkSynchronized(BrewPotency.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GardenersInfusion>> GARDENERS_INFUSION =
            REGISTRY.register("gardeners_infusion", () -> DataComponentType.<GardenersInfusion>builder()
                    .persistent(GardenersInfusion.CODEC)
                    .networkSynchronized(GardenersInfusion.STREAM_CODEC)
                    .build());

    private ModComponentsNeoforge() {}

    public static void init() {
        ModComponents.BREW_POTENCY = BREW_POTENCY.get();
        ModComponents.GARDENERS_INFUSION = GARDENERS_INFUSION.get();
    }
}
