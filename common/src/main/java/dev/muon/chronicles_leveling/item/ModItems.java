package dev.muon.chronicles_leveling.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Function;

/**
 * Item factories take a {@link ResourceKey} and stamp it onto the {@link Item.Properties} via
 * {@code setId} so both loaders register with the same id without per-loader item subclasses. Holder
 * fields are populated by each loader's registration code so common code can reach the resulting
 * {@link Holder} once registration completes.
 */
public final class ModItems {

    private ModItems() {}

    public static Holder<Item> LESSER_ORB_OF_REGRET;
    public static Holder<Item> GREATER_ORB_OF_REGRET;
    public static Holder<Item> TOME;

    public static final Function<ResourceKey<Item>, OrbOfRegretItem> LESSER_ORB_OF_REGRET_FACTORY = key -> {
        Item.Properties props = new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .setId(key);
        return new OrbOfRegretItem(props);
    };

    public static final Function<ResourceKey<Item>, GreaterOrbOfRegretItem> GREATER_ORB_OF_REGRET_FACTORY = key -> {
        Item.Properties props = new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
                .setId(key);
        return new GreaterOrbOfRegretItem(props);
    };

    public static final Function<ResourceKey<Item>, TomeItem> TOME_FACTORY = key -> {
        Item.Properties props = new Item.Properties()
                .stacksTo(64)
                .rarity(Rarity.UNCOMMON)
                .setId(key);
        return new TomeItem(props);
    };
}
