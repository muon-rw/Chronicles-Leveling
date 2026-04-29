package dev.muon.chronicles_leveling.item;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItemsFabric {

    public static final Holder<Item> LESSER_ORB_OF_REGRET = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            ChroniclesLeveling.id("lesser_orb_of_regret"),
            ModItems.LESSER_ORB_OF_REGRET_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, ChroniclesLeveling.id("lesser_orb_of_regret"))
            )
    );

    public static final Holder<Item> GREATER_ORB_OF_REGRET = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            ChroniclesLeveling.id("greater_orb_of_regret"),
            ModItems.GREATER_ORB_OF_REGRET_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, ChroniclesLeveling.id("greater_orb_of_regret"))
            )
    );

    public static final Holder<Item> TOME = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            ChroniclesLeveling.id("tome"),
            ModItems.TOME_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, ChroniclesLeveling.id("tome"))
            )
    );

    private ModItemsFabric() {}

    public static void init() {
        ModItems.LESSER_ORB_OF_REGRET = LESSER_ORB_OF_REGRET;
        ModItems.GREATER_ORB_OF_REGRET = GREATER_ORB_OF_REGRET;
        ModItems.TOME = TOME;
    }
}
