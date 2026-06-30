package dev.muon.chronicles_leveling.skill.fishing;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.SkillEnchantApply;
import dev.muon.chronicles_leveling.skill.catalog.FishingSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Loader-agnostic Fishing catch-loot perks, applied to the reeled-in drop list (Fabric: the {@code retrieve}
 * loot wrap; NeoForge: {@code ItemFishedEvent}). XP is granted on the base catch before this runs, so bonus
 * loot here doesn't inflate skill XP.
 */
public final class FishingCatchHandler {

    private FishingCatchHandler() {}

    /** Bonus treasure-tier items Fortune's Catch can surface (a curated subset of the vanilla treasure feel). */
    private static final List<net.minecraft.world.item.Item> TREASURE_POOL =
            List.of(Items.NAUTILUS_SHELL, Items.NAME_TAG, Items.SADDLE, Items.LILY_PAD);

    public static void modifyCatch(ServerPlayer player, List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = player.getRandom();
        var cfg = Configs.SKILLS.fishing;
        int baseCatchSize = drops.size();   // captured before bonus rolls so Big Catch only copies a genuine catch

        // Fisher's Feast: caught fish come out cooked (more hunger, never raw).
        if (SkillEffects.has(player, FishingSkill.FISHER_FEAST)) {
            for (int i = 0; i < drops.size(); i++) {
                ItemStack cooked = cooked(drops.get(i));
                if (cooked != null) {
                    drops.set(i, cooked);
                }
            }
        }
        // Enchanted Catch + Leviathan's Gift: enrich any fished-up gear with extra enchantments, then raise every
        // enchantment's level (clamped to each enchantment's max; max-level-1 enchants like Mending are left as-is).
        int extraEnchants = (int) Math.floor(SkillEffects.get(player, FishingSkill.FISHED_EXTRA_ENCHANTS));
        int levelBoost = (int) Math.floor(SkillEffects.get(player, FishingSkill.FISHED_ENCHANT_LEVEL_BOOST));
        if (extraEnchants > 0 || levelBoost > 0) {
            int enchantLevel = cfg.fishedEnchantLevel.get();
            for (ItemStack stack : drops) {
                SkillEnchantApply.addExtraEnchantments(stack, extraEnchants, enchantLevel, random, level.registryAccess());
                SkillEnchantApply.boostEnchantLevels(stack, levelBoost, true);
            }
        }
        // Fortune's Catch (treasure): chance to surface a bonus treasure-tier item.
        double treasure = SkillEffects.get(player, FishingSkill.TREASURE_BONUS);
        if (treasure > 0 && random.nextDouble() < treasure) {
            drops.add(new ItemStack(TREASURE_POOL.get(random.nextInt(TREASURE_POOL.size()))));
        }
        // Big Catch: each rank rolls an independent chance to reel in an extra copy of a genuine catch item.
        int catchRolls = (int) Math.round(SkillEffects.get(player, FishingSkill.MULTI_CATCH_ROLLS));
        if (catchRolls > 0) {
            double chance = cfg.bigCatchChancePerRoll.get();
            for (int i = 0; i < catchRolls; i++) {
                if (random.nextDouble() < chance) {
                    drops.add(drops.get(random.nextInt(baseCatchSize)).copy());
                }
            }
        }
    }

    private static ItemStack cooked(ItemStack raw) {
        if (raw.is(Items.COD)) {
            return new ItemStack(Items.COOKED_COD, raw.getCount());
        }
        if (raw.is(Items.SALMON)) {
            return new ItemStack(Items.COOKED_SALMON, raw.getCount());
        }
        return null;
    }
}
