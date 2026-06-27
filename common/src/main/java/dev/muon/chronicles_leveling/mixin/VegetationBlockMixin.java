package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.config.Configs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mycologist rank 1 (global, config-gated by {@code mushroomsPersistInLight}): keep an already-placed mushroom from
 * being broken by a neighbor update just because it's too bright. Vanilla's {@code updateShape} returns air when
 * {@code canSurvive} fails; this restores the mushroom when only the light requirement failed and it still sits on a
 * solid block. A mushroom whose floor was removed still pops off as usual.
 */
@Mixin(value = VegetationBlock.class, remap = false)
public abstract class VegetationBlockMixin {

    @ModifyReturnValue(method = "updateShape", at = @At("RETURN"), remap = false)
    private BlockState chronicles_leveling$keepMushroomInLight(BlockState result,
            @Local(argsOnly = true, ordinal = 0) BlockState state,
            @Local(argsOnly = true) LevelReader level,
            @Local(argsOnly = true, ordinal = 0) BlockPos pos) {
        if (!result.isAir() || !(state.getBlock() instanceof MushroomBlock)
                || !Configs.SKILLS.herbalism.mushroomsPersistInLight.get()) {
            return result;
        }
        return level.getBlockState(pos.below()).isSolidRender() ? state : result;
    }
}
