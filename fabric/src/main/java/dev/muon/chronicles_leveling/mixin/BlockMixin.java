package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.skill.gather.GatherProcRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fabric gathering loot/yield seam (the NeoForge side rides {@code BlockDropsEvent} instead). Wrapping the
 * {@code getDrops} call inside {@code Block#dropResources} is a PRE-spawn seam: the returned list is what gets
 * popped, so the shared {@link GatherProcRouter} can replace drops (Smelter's Touch), append bonus copies, and
 * append pool items, not just add after the fact. Gated to a {@code ServerPlayer} breaker. (Loom remaps the
 * Mojmap targets; no {@code remap = false}, matching the other Fabric mixins.)
 */
@Mixin(Block.class)
public abstract class BlockMixin {

    @WrapOperation(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;"))
    private static List<ItemStack> chronicles_leveling$gatherDrops(BlockState state, ServerLevel level, BlockPos pos,
                                                                   BlockEntity blockEntity, Entity breaker,
                                                                   ItemInstance tool, Operation<List<ItemStack>> original) {
        List<ItemStack> natural = original.call(state, level, pos, blockEntity, breaker, tool);
        if (breaker instanceof ServerPlayer player && tool instanceof ItemStack stack) {
            List<ItemStack> drops = new ArrayList<>(natural);
            GatherProcRouter.modifyDrops(player, level, pos, state, stack, drops);
            return drops;
        }
        return natural;
    }
}
