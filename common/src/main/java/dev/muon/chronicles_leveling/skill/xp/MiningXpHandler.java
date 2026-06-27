package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.ConfigSkills;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEnchants;
import dev.muon.chronicles_leveling.skill.SkillTags;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Tier-bonus tags are resolved lazily and cached because the config entries
 * are stable for the life of a server tick; re-creating the {@code TagKey}
 * objects every block break adds GC pressure on a hot path. The {@code #c:ores}
 * handle is shared via {@link SkillTags#ORES} (also read by the loot-proc seam).
 */
public final class MiningXpHandler {

    private MiningXpHandler() {}

    private static List<? extends ConfigSkills.TierBonus> cachedSource;
    private static TierBonusEntry[] resolvedTiers = new TierBonusEntry[0];

    public static void onBlockBreak(ServerPlayer player, BlockState state, ItemStack tool) {
        ConfigSkills.Mining cfg = Configs.SKILLS.mining;
        float hardness = state.getBlock().defaultDestroyTime();
        double xp = cfg.xpPerHardness.evalSafe(Map.of('h', (double) Math.max(0f, hardness)), 0.0);
        if (xp <= 0) return;

        if (state.is(SkillTags.ORES)) {
            xp *= SkillEnchants.hasSilkTouch(player, tool) ? cfg.silkTouchOreMultiplier.get() : cfg.oreMultiplier.get();
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

    private record TierBonusEntry(TagKey<Block> tag, double multiplier) {}
}
