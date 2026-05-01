package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public final class EnchantingXpHandler {

    private EnchantingXpHandler() {}

    public static void onTableEnchant(ServerPlayer player, int levelCost) {
        double xp = Configs.SKILLS.enchanting.xpPerTableEnchant.evalSafe(
                Map.of('c', (double) levelCost), 0.0);
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
