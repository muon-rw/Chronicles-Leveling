package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.skill.gather.MycologyHandler;
import net.minecraft.world.level.block.MushroomBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mycologist rank 1: while a holder is placing a mushroom item, let it pass the placement survival check on any solid
 * block (off its usual nylium). The flag is set only for the duration of that placement ({@code BlockItemMixin}),
 * so it does not relax survival for anyone else; persistence afterward is handled by {@code VegetationBlockMixin}.
 */
@Mixin(value = MushroomBlock.class, remap = false)
public abstract class MushroomBlockMixin {

    @ModifyReturnValue(method = "canSurvive", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$placeAnywhere(boolean original) {
        return original || MycologyHandler.isPlaceAnywhere();
    }
}
