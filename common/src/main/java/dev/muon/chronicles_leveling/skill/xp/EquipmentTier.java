package dev.muon.chronicles_leveling.skill.xp;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;

/**
 * Cross-loader detection of a tool/armor item's vanilla tier via the
 * {@link DataComponents#REPAIRABLE} component. Both {@link
 * net.minecraft.world.item.ToolMaterial} and {@link
 * net.minecraft.world.item.equipment.ArmorMaterial} wire their repair
 * ingredient through it, so probing for the canonical tier ingot identifies
 * tier reliably for vanilla and any modded equipment using vanilla repair items.
 */
public final class EquipmentTier {

    private EquipmentTier() {}

    public enum Tier { NONE, WOOD, STONE, COPPER, GOLD, IRON, DIAMOND, NETHERITE }

    public static Tier of(ItemStack stack) {
        if (stack.isEmpty()) return Tier.NONE;
        Repairable repairable = stack.get(DataComponents.REPAIRABLE);
        if (repairable == null) return Tier.NONE;
        HolderSet<Item> items = repairable.items();

        if (items.contains(Items.NETHERITE_INGOT.builtInRegistryHolder()))  return Tier.NETHERITE;
        if (items.contains(Items.DIAMOND.builtInRegistryHolder()))          return Tier.DIAMOND;
        if (items.contains(Items.IRON_INGOT.builtInRegistryHolder()))       return Tier.IRON;
        if (items.contains(Items.GOLD_INGOT.builtInRegistryHolder()))       return Tier.GOLD;
        if (items.contains(Items.COPPER_INGOT.builtInRegistryHolder()))     return Tier.COPPER;
        if (items.contains(Items.COBBLESTONE.builtInRegistryHolder()))      return Tier.STONE;
        if (items.contains(Items.OAK_PLANKS.builtInRegistryHolder()))       return Tier.WOOD;
        return Tier.NONE;
    }
}
