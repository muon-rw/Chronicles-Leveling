package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.ConfigSkills;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public final class SmithingXpHandler {

    private SmithingXpHandler() {}

    public static void onCraft(ServerPlayer player, ItemStack crafted) {
        if (crafted.isEmpty()) return;
        ConfigSkills.Smithing cfg = Configs.SKILLS.smithing;
        double base = cfg.baseXp.get();
        if (base <= 0) return;

        double stackMult = cfg.stackMultiplier.evalSafe(
                Map.of('n', (double) crafted.getCount()), 1.0);
        PlayerSkillManager.grantXp(player, Skills.SMITHING,
                base * stackMult * multiplierFor(EquipmentTier.of(crafted), cfg));
    }

    private static double multiplierFor(EquipmentTier.Tier tier, ConfigSkills.Smithing cfg) {
        return switch (tier) {
            case WOOD       -> cfg.woodMultiplier.get();
            case STONE      -> cfg.stoneMultiplier.get();
            case COPPER     -> cfg.copperMultiplier.get();
            case GOLD       -> cfg.goldMultiplier.get();
            case IRON       -> cfg.ironMultiplier.get();
            case DIAMOND    -> cfg.diamondMultiplier.get();
            case NETHERITE  -> cfg.netheriteMultiplier.get();
            case NONE       -> 1.0;
        };
    }
}
