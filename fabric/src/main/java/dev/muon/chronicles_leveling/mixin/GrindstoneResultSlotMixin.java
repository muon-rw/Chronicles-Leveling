package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Targets the anonymous result Slot compiled as {@code GrindstoneMenu$4} —
 *  same anchor NeoForge's {@code CommonHooks.onGrindstoneTake} patch uses.
 *  If MC reorders the constructor's anonymous bodies, only the {@code targets}
 *  string here needs to follow. */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4", remap = false)
public abstract class GrindstoneResultSlotMixin {

    @Shadow
    private int getExperienceAmount(Level level) { throw new AssertionError(); }

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$grantGrindstoneXp(Player player, ItemStack carried, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        int xp = getExperienceAmount(player.level());
        if (xp <= 0) return;
        EnchantingXpHandler.onGrindstoneTake(serverPlayer, xp);
    }
}
