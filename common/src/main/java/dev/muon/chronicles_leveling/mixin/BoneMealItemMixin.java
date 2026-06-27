package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.gather.MycologyHandler;
import dev.muon.chronicles_leveling.skill.xp.HerbalismXpHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.MushroomBlock;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mycologist bone-meal perks, wrapped around {@code useOn} as one operation. Rank 2 sets the grow-anywhere flag
 * ({@code FungusGrowthMixin} reads it) for the duration of a holder's use, so mushrooms / nether fungi grow regardless
 * of surface, light, or nylium; the flag is always cleared via try/finally even if placement throws. Rank 3 then
 * spreads mycelium around a bonemealed mushroom. {@code remap = false} (Mojmap fork).
 */
@Mixin(value = BoneMealItem.class, remap = false)
public abstract class BoneMealItemMixin {

    @WrapMethod(method = "useOn")
    private InteractionResult chronicles_leveling$bonemealPerks(UseOnContext context, Operation<InteractionResult> original) {
        ServerPlayer player = !context.getLevel().isClientSide()
                && context.getPlayer() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        int rank = player != null ? MycologyHandler.rank(player) : 0;
        boolean growAnywhere = rank >= 2;
        BlockPos pos = context.getClickedPos();
        boolean spreadsMycelium = rank >= 3 && context.getLevel().getBlockState(pos).getBlock() instanceof MushroomBlock;

        if (growAnywhere) {
            MycologyHandler.setGrowAnywhere(true);
        }
        InteractionResult result;
        try {
            result = original.call(context);
        } finally {
            if (growAnywhere) {
                MycologyHandler.setGrowAnywhere(false);
            }
        }
        if (player != null && result instanceof InteractionResult.Success) {
            HerbalismXpHandler.onBonemeal(player);
            if (spreadsMycelium && context.getLevel() instanceof ServerLevel level) {
                MycologyHandler.spreadMycelium(level, pos, Configs.SKILLS.herbalism.mycologistMyceliumSpreadRadius.get());
            }
        }
        return result;
    }
}
