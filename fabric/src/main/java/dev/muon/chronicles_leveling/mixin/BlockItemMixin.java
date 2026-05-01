package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.FarmingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockItem.class, remap = false)
public abstract class BlockItemMixin {

    @Inject(method = "place", at = @At("RETURN"), remap = false)
    private void chronicles_leveling$grantFarmingXp(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != InteractionResult.SUCCESS) return;
        if (context.getLevel().isClientSide()) return;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        FarmingXpHandler.onPlant(player, context.getLevel().getBlockState(context.getClickedPos()));
    }
}
