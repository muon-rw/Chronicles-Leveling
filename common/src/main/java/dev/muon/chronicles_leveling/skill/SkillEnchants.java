package dev.muon.chronicles_leveling.skill;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.lang.ref.WeakReference;

/**
 * Shared enchantment queries for skill handlers. The Silk Touch {@link Holder} is resolved lazily and cached
 * against the registry lookup (a {@link WeakReference} so a world unload can't pin it), since these run on the
 * hot block-break path. Read by both {@code MiningXpHandler} (XP) and {@code GatherProcRouter} (loot procs).
 */
public final class SkillEnchants {

    private SkillEnchants() {}

    private static WeakReference<HolderLookup.RegistryLookup<Enchantment>> cachedLookup = new WeakReference<>(null);
    private static Holder<Enchantment> cachedSilkTouch;
    private static Holder<Enchantment> cachedFortune;

    /** Whether the tool carries Silk Touch (so callers can let Silk Touch's preserve-the-block intent win). */
    public static boolean hasSilkTouch(ServerPlayer player, ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        ItemEnchantments enchants = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchants.isEmpty()) {
            return false;
        }
        return enchants.getLevel(silkTouch(player)) > 0;
    }

    /**
     * A copy of the tool with {@code level} ADDED on top of its existing Fortune (Natural Fortune's "fortune
     * without the enchantment": a re-roll of the loot with this copy gives the vanilla Fortune drops). Additive
     * rather than a floor, so the perk still helps a player whose pickaxe is already Fortune-enchanted.
     */
    public static ItemStack fortuneBoosted(ServerPlayer player, ItemStack tool, int level) {
        ItemStack copy = tool.copy();
        Holder<Enchantment> fortune = fortune(player);
        int existing = copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(fortune);
        copy.enchant(fortune, existing + level);   // enchant() upgrades; existing+level always exceeds existing
        return copy;
    }

    private static Holder<Enchantment> silkTouch(ServerPlayer player) {
        refresh(player);
        return cachedSilkTouch;
    }

    private static Holder<Enchantment> fortune(ServerPlayer player) {
        refresh(player);
        return cachedFortune;
    }

    private static void refresh(ServerPlayer player) {
        HolderLookup.RegistryLookup<Enchantment> lookup =
                player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (cachedLookup.get() != lookup) {
            cachedSilkTouch = lookup.getOrThrow(Enchantments.SILK_TOUCH);
            cachedFortune = lookup.getOrThrow(Enchantments.FORTUNE);
            cachedLookup = new WeakReference<>(lookup);
        }
    }
}
