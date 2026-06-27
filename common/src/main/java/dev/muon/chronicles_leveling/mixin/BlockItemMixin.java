package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.muon.chronicles_leveling.skill.gather.MycologyHandler;
import dev.muon.chronicles_leveling.skill.xp.HerbalismXpHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.MushroomBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Both {@code BlockItem.place} hooks, common to both loaders ({@code remap = false}, Mojmap fork):
 * <ul>
 *   <li>Mycologist rank 1: a holder placing a mushroom off nylium. The placement is bracketed with the
 *       place-anywhere flag so the mushroom's survival check ({@code MushroomBlockMixin}) passes on any solid
 *       block; cleared in a finally so it never leaks to a later placement or actor.</li>
 *   <li>Herbalism planting XP, granted only when a player actually places a seed/plant block item. Hooking
 *       {@code place} (rather than a generic block-place event) is what keeps bone-meal-grown or otherwise
 *       auto-placed plants from counting as plantings; bone meal grants its own small XP via {@code BoneMealItemMixin}.</li>
 * </ul>
 * The XP inject at {@code RETURN} fires inside the place-anywhere wrap, at the original method's return.
 */
@Mixin(value = BlockItem.class, remap = false)
public abstract class BlockItemMixin {

    @WrapMethod(method = "place")
    private InteractionResult chronicles_leveling$mushroomPlaceAnywhere(BlockPlaceContext context, Operation<InteractionResult> original) {
        boolean relaxed = ((BlockItem) (Object) this).getBlock() instanceof MushroomBlock
                && context.getPlayer() instanceof ServerPlayer player
                && MycologyHandler.rank(player) >= 1;
        if (relaxed) {
            MycologyHandler.setPlaceAnywhere(true);
        }
        try {
            return original.call(context);
        } finally {
            if (relaxed) {
                MycologyHandler.setPlaceAnywhere(false);
            }
        }
    }

    @Inject(method = "place", at = @At("RETURN"), remap = false)
    private void chronicles_leveling$grantHerbalismXp(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != InteractionResult.SUCCESS) return;
        if (!(context.getLevel() instanceof ServerLevel level)) return;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        HerbalismXpHandler.onPlant(player, level, context.getClickedPos());
    }
}
