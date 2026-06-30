package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.muon.chronicles_leveling.skill.fishing.FishingHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Storm God rank 3: Riptide works with no water or rain. Lifts ONLY the {@code isInWaterOrRain()} gate in both
 * Trident use sites; the Riptide enchant is still required (the spin-attack-strength check is left untouched).
 */
@Mixin(value = TridentItem.class, remap = false)
public abstract class TridentItemMixin {

    @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"), remap = false)
    private boolean chronicles_leveling$stormRiptideUse(boolean original, Level level, Player player, InteractionHand hand) {
        return original || FishingHooks.canStormRiptide(player);
    }

    @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"), remap = false)
    private boolean chronicles_leveling$stormRiptideRelease(boolean original, ItemStack itemStack, Level level, LivingEntity entity, int remainingTime) {
        return original || (entity instanceof Player player && FishingHooks.canStormRiptide(player));
    }
}
