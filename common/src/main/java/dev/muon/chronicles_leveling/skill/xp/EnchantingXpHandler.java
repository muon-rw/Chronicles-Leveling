package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.ConfigSkills;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.Map;

public final class EnchantingXpHandler {

    private EnchantingXpHandler() {}

    /** Sums per-enchant XP over the roll actually applied: applied level, rarity weight, and treasure status. */
    public static void onTableEnchant(ServerPlayer player, List<EnchantmentInstance> applied) {
        ConfigSkills.Enchanting cfg = Configs.SKILLS.enchanting;
        double xp = 0.0;
        for (EnchantmentInstance enchantment : applied) {
            double treasure = enchantment.enchantment().is(EnchantmentTags.TREASURE) ? 1.0 : 0.0;
            xp += cfg.xpPerEnchant.evalSafe(
                    Map.of('l', (double) enchantment.level(), 'w', (double) enchantment.weight(), 't', treasure), 0.0);
        }
        PlayerSkillManager.grantXp(player, Skills.ENCHANTING, xp);
    }

    public static void onGrindstoneTake(ServerPlayer player, int xpAwarded) {
        double xp = Configs.SKILLS.enchanting.xpPerGrindstone.evalSafe(
                Map.of('x', (double) xpAwarded), 0.0);
        PlayerSkillManager.grantXp(player, Skills.ENCHANTING, xp);
    }

    public static void onAnvilTake(ServerPlayer player, int levelCost) {
        double xp = Configs.SKILLS.enchanting.xpPerAnvil.evalSafe(
                Map.of('c', (double) levelCost), 0.0);
        PlayerSkillManager.grantXp(player, Skills.ENCHANTING, xp);
    }
}
