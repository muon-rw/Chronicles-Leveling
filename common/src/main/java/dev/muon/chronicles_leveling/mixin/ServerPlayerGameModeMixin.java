package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.skill.xp.HerbalismXpHandler;
import dev.muon.chronicles_leveling.skill.xp.MiningXpHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mining + Herbalism block-break XP, one common seam for both loaders. Grants at the {@code playerDestroy}
 * call inside {@code destroyBlock}: that is past the {@code preventsBlockDrops} creative early-return and inside
 * the {@code changed && canDestroy} correct-tool gate, so a creative break or a fruitless wrong-tool break grants
 * nothing. This is the same productive-break condition NeoForge's {@code BlockDropsEvent} fires under, so it
 * collapses two divergent loader seams (a Fabric-API break callback that fired on EVERY break, and the NeoForge
 * drops event) into one. {@code adjustedState} / {@code destroyedWith} are the exact state + tool the drops path
 * sees ({@code destroyedWith} is the pre-{@code mineBlock} tool copy, matching {@code BlockDropsEvent.getTool}).
 */
@Mixin(value = ServerPlayerGameMode.class, remap = false)
public abstract class ServerPlayerGameModeMixin {

    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void chronicles_leveling$grantBlockBreakXp(BlockPos pos, CallbackInfoReturnable<Boolean> cir,
            @Local(name = "adjustedState") BlockState adjustedState, @Local(name = "destroyedWith") ItemStack destroyedWith) {
        MiningXpHandler.onBlockBreak(this.player, adjustedState, destroyedWith);
        HerbalismXpHandler.onBlockBreak(this.player, adjustedState);
    }
}
