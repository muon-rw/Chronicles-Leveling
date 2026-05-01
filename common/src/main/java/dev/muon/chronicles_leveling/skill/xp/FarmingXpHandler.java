package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plant detection accepts any {@link VegetationBlock} (covers seeds, sweet
 * berries, saplings); harvest detection narrows to {@link CropBlock} at max
 * age so misclicks on growing crops can't farm XP.
 */
public final class FarmingXpHandler {

    private FarmingXpHandler() {}

    public static void onTill(ServerPlayer player) {
        PlayerSkillManager.grantXp(player, Skills.FARMING, Configs.SKILLS.farming.xpPerTill.get());
    }

    public static void onPlant(ServerPlayer player, BlockState placed) {
        if (!(placed.getBlock() instanceof VegetationBlock)) return;
        PlayerSkillManager.grantXp(player, Skills.FARMING, Configs.SKILLS.farming.xpPerPlant.get());
    }

    // TODO: Compat for RightClickHarvest, or similar
    public static void onBlockBreak(ServerPlayer player, BlockState state) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        PlayerSkillManager.grantXp(player, Skills.FARMING, Configs.SKILLS.farming.xpPerHarvest.get());
    }
}
