package dev.muon.chronicles_leveling.skill;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Optional;

/**
 * Shared enchantment enrichment for perk handlers (Speech trades, Fishing catches): add extra random
 * enchantments to gear/books, and raise the levels of enchantments already present.
 */
public final class SkillEnchantApply {

    private SkillEnchantApply() {}

    /** Rolls {@code count} extra applicable enchantments onto gear or an enchanted book; plain books and non-gear are skipped. */
    public static void addExtraEnchantments(ItemStack stack, int count, int enchantLevel, RandomSource random, RegistryAccess registries) {
        if (count <= 0 || stack.isEmpty() || stack.is(Items.BOOK)) {
            return;
        }
        boolean enchantedBook = stack.is(Items.ENCHANTED_BOOK);
        if (!enchantedBook && !stack.has(DataComponents.ENCHANTABLE)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            if (enchantedBook) {
                addRandomBookEnchantment(stack, random, enchantLevel, registries);
            } else {
                EnchantmentHelper.enchantItem(random, stack, enchantLevel, registries, Optional.empty());
            }
        }
    }

    /**
     * Raises every enchantment present by {@code boost} levels, skipping max-level-1 enchants (Mending, Silk Touch);
     * clamps each to its own max when {@code clampToMax}, else lets it exceed the vanilla max.
     */
    public static void boostEnchantLevels(ItemStack stack, int boost, boolean clampToMax) {
        if (boost <= 0) {
            return;
        }
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            for (Holder<Enchantment> enchantment : List.copyOf(mutable.keySet())) {
                int max = enchantment.value().getMaxLevel();
                if (max > 1) {
                    int raised = mutable.getLevel(enchantment) + boost;
                    mutable.set(enchantment, clampToMax ? Math.min(max, raised) : raised);
                }
            }
        });
    }

    private static void addRandomBookEnchantment(ItemStack book, RandomSource random, int enchantLevel, RegistryAccess registries) {
        ItemStack rolled = EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), enchantLevel, registries, Optional.empty());
        ItemEnchantments rolledEnchants = rolled.get(DataComponents.STORED_ENCHANTMENTS);
        if (rolledEnchants == null) {
            return;
        }
        for (Holder<Enchantment> enchantment : rolledEnchants.keySet()) {
            book.enchant(enchantment, rolledEnchants.getLevel(enchantment));
        }
    }
}
