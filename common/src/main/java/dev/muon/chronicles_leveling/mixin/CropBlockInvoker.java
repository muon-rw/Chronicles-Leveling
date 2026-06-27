package dev.muon.chronicles_leveling.mixin;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the crop's seed item so Auto-Replant can require (and spend) a matching seed from the player. */
@Mixin(value = CropBlock.class, remap = false)
public interface CropBlockInvoker {

    @Invoker("getBaseSeedId")
    ItemLike chronicles_leveling$getBaseSeedId();
}
