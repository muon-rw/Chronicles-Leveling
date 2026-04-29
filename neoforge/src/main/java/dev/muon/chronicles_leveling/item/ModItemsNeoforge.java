package dev.muon.chronicles_leveling.item;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItemsNeoforge {

    public static final DeferredRegister<Item> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.ITEM, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<Item, OrbOfRegretItem> LESSER_ORB_OF_REGRET =
            REGISTRY.register("lesser_orb_of_regret", () -> ModItems.LESSER_ORB_OF_REGRET_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, ChroniclesLeveling.id("lesser_orb_of_regret"))
            ));

    public static final DeferredHolder<Item, GreaterOrbOfRegretItem> GREATER_ORB_OF_REGRET =
            REGISTRY.register("greater_orb_of_regret", () -> ModItems.GREATER_ORB_OF_REGRET_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, ChroniclesLeveling.id("greater_orb_of_regret"))
            ));

    public static final DeferredHolder<Item, TomeItem> TOME =
            REGISTRY.register("tome", () -> ModItems.TOME_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, ChroniclesLeveling.id("tome"))
            ));

    private ModItemsNeoforge() {}

    /** Publish loader-side holders to common. Call after deferred registries fire. */
    public static void init() {
        ModItems.LESSER_ORB_OF_REGRET = LESSER_ORB_OF_REGRET;
        ModItems.GREATER_ORB_OF_REGRET = GREATER_ORB_OF_REGRET;
        ModItems.TOME = TOME;
    }
}
