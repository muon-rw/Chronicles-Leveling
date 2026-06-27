package dev.muon.chronicles_leveling.item;

import dev.muon.chronicles_leveling.config.Configs;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PotionItem;

/**
 * Config-backed potion stacking. The loader item-component events read these to raise potions'
 * {@code MAX_STACK_SIZE}; the brewing perks read {@link #maxStack()} as the ceiling for Master Brewer's bonus.
 * {@code PotionItem} is the exact common base of POTION, SPLASH_POTION, and LINGERING_POTION (tipped arrows extend
 * {@code ArrowItem}, so they are excluded).
 */
public final class StackablePotions {

    private StackablePotions() {}

    public static boolean enabled() {
        return Configs.SKILLS.alchemy.stackablePotions.get() && maxStack() > 1;
    }

    public static int maxStack() {
        return Mth.clamp(Configs.SKILLS.alchemy.maxPotionStackSize.get(), 1, 99);
    }

    public static boolean isPotion(Item item) {
        return item instanceof PotionItem;
    }
}
