package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.gather.GatherProcRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plant detection accepts any {@link VegetationBlock} (covers seeds, sweet
 * berries, saplings); harvest detection narrows to {@link CropBlock} at max
 * age so misclicks on growing crops can't farm XP.
 */
public final class HerbalismXpHandler {

    private HerbalismXpHandler() {}

    public static void onTill(ServerPlayer player) {
        PlayerSkillManager.grantXp(player, Skills.HERBALISM, Configs.SKILLS.herbalism.xpPerTill.get());
    }

    public static void onBonemeal(ServerPlayer player) {
        PlayerSkillManager.grantXp(player, Skills.HERBALISM, Configs.SKILLS.herbalism.xpPerBonemeal.get());
    }

    public static void onPlant(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState placed = level.getBlockState(pos);
        if (!(placed.getBlock() instanceof VegetationBlock)) return;
        PlayerSkillManager.grantXp(player, Skills.HERBALISM, Configs.SKILLS.herbalism.xpPerPlant.get());
        GatherProcRouter.greenThumbAfterPlant(player, level, pos);
    }

    // TODO: Compat for RightClickHarvest, or similar
    public static void onBlockBreak(ServerPlayer player, BlockState state) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        PlayerSkillManager.grantXp(player, Skills.HERBALISM, Configs.SKILLS.herbalism.xpPerHarvest.get());
    }
}
