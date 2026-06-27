package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.gather.GatherProcRouter;
import dev.muon.chronicles_leveling.skill.util.AbilityTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Herbalism capstone (active): grow every crop in a radius to full, harvest it, and replant it. The harvest honors the
 * holder's earned Herbalism bonuses just like a manual break would: Cultivation / Toxin Harvest enrich the drops, and
 * Green Thumb may auto-bonemeal each replanted crop. The replant itself is free (the active already costs stamina).
 */
public final class BountifulHarvestAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/bountiful_harvest");

    public BountifulHarvestAbility() {
        super(ID, Skills.HERBALISM,
                Configs.SKILLS.herbalism.bountifulHarvestCooldownTicks.get(),
                AbilityCost.stamina(Configs.SKILLS.herbalism.bountifulHarvestStaminaCost.get().floatValue()));
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        return player.level() instanceof ServerLevel level
                && AbilityTargets.anyBlockWithin(level, player.blockPosition(), radius(player), 1,
                        state -> state.getBlock() instanceof CropBlock);
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.no_crops");
    }

    @Override
    public void run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int radius = radius(player);
        BlockPos center = player.blockPosition();
        ItemStack tool = player.getMainHandItem();
        boolean harvested = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            if (!(level.getBlockState(pos).getBlock() instanceof CropBlock crop)) {
                continue;
            }
            BlockPos at = pos.immutable();
            BlockState mature = crop.getStateForAge(crop.getMaxAge());
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(mature, level, at, null, player, tool));
            GatherProcRouter.applyCropHarvestBonuses(player, drops);
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    Block.popResource(level, at, drop);
                }
            }
            level.setBlockAndUpdate(at, crop.getStateForAge(0));
            GatherProcRouter.greenThumbAfterPlant(player, level, at);
            harvested = true;
        }
        if (harvested) {
            level.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    private static int radius(ServerPlayer player) {
        int rank = Math.max(1, PlayerSkillManager.get(player).get(Skills.HERBALISM).rankOf("bountiful_harvest"));
        var h = Configs.SKILLS.herbalism;
        return (int) Math.round(h.bountifulHarvestRadiusBase.get() + h.bountifulHarvestRadiusPerRank.get() * (rank - 1));
    }
}
