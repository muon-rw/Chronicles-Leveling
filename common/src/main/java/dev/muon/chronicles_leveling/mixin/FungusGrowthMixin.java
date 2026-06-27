package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.skill.gather.MycologyHandler;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mycologist (Herbalism) grow-anywhere: both mushroom and nether-fungus blocks gate bone-meal growth on
 * {@code isValidBonemealTarget} (light + surface for mushrooms, a matching nylium below for fungi), with no player
 * in scope. While a perk-holder's bone-meal use has {@link MycologyHandler#isGrowAnywhere} set, treat that check as
 * satisfied so the huge mushroom / fungus grows regardless. Both blocks share the {@code BonemealableBlock} signature,
 * so one mixin targets both.
 */
@Mixin({MushroomBlock.class, NetherFungusBlock.class})
public abstract class FungusGrowthMixin {

    @ModifyReturnValue(method = "isValidBonemealTarget", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$mycologistGrowAnywhere(boolean original) {
        return original || MycologyHandler.isGrowAnywhere();
    }
}
