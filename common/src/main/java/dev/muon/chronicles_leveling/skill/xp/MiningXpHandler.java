package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.ConfigSkills;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

/**
 * The {@code #c:ores} tag is the cross-loader convention published by both
 * Fabric Convention Tags v2 and NeoForge, so a single common
 * {@link TagKey#create} resolves on either side without a per-loader bridge.
 *
 * <p>Tier-bonus tags are resolved lazily and cached because the config entries
 * are stable for the life of a server tick — re-creating the {@code TagKey}
 * objects every block break adds GC pressure on a hot path.
 */
public final class MiningXpHandler {

    private MiningXpHandler() {}

    private static final TagKey<Block> ORES = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

    private static List<? extends ConfigSkills.TierBonus> cachedSource;
    private static TierBonusEntry[] resolvedTiers = new TierBonusEntry[0];

    private static WeakReference<HolderLookup.RegistryLookup<Enchantment>> cachedEnchantLookup = new WeakReference<>(null);
    private static Holder<Enchantment> cachedSilkTouch;

    public static void onBlockBreak(ServerPlayer player, BlockState state, ItemStack tool) {
        ConfigSkills.Mining cfg = Configs.SKILLS.mining;
        float hardness = state.getBlock().defaultDestroyTime();
        double xp = cfg.xpPerHardness.evalSafe(Map.of('h', (double) Math.max(0f, hardness)), 0.0);
        if (xp <= 0) return;

        if (state.is(ORES)) {
            xp *= hasSilkTouch(player, tool) ? cfg.silkTouchOreMultiplier.get() : cfg.oreMultiplier.get();
            if (xp <= 0) return;
        }

        for (TierBonusEntry entry : tierEntries(cfg)) {
            if (state.is(entry.tag)) {
                xp *= entry.multiplier;
                break;
            }
        }
        PlayerSkillManager.grantXp(player, Skills.MINING, xp);
    }

    private static TierBonusEntry[] tierEntries(ConfigSkills.Mining cfg) {
        List<? extends ConfigSkills.TierBonus> source = cfg.tierBonuses.get();
        if (source != cachedSource) {
            TierBonusEntry[] entries = new TierBonusEntry[source.size()];
            for (int i = 0; i < entries.length; i++) {
                ConfigSkills.TierBonus bonus = source.get(i);
                entries[i] = new TierBonusEntry(
                        TagKey.create(Registries.BLOCK, bonus.tag.get()),
                        bonus.multiplier.get());
            }
            resolvedTiers = entries;
            cachedSource = source;
        }
        return resolvedTiers;
    }

    private static boolean hasSilkTouch(ServerPlayer player, ItemStack tool) {
        if (tool == null || tool.isEmpty()) return false;
        ItemEnchantments enchants = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchants.isEmpty()) return false;
        return enchants.getLevel(silkTouchHolder(player)) > 0;
    }

    private static Holder<Enchantment> silkTouchHolder(ServerPlayer player) {
        HolderLookup.RegistryLookup<Enchantment> lookup =
                player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (cachedEnchantLookup.get() != lookup) {
            cachedSilkTouch = lookup.getOrThrow(Enchantments.SILK_TOUCH);
            cachedEnchantLookup = new WeakReference<>(lookup);
        }
        return cachedSilkTouch;
    }

    private record TierBonusEntry(TagKey<Block> tag, double multiplier) {}
}
