package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.SpeechXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Speech XP: taming a tamable animal (manually or via Beast Whisperer) grants the owner Speech XP. */
@Mixin(value = TamableAnimal.class, remap = false)
public abstract class TamableAnimalMixin {

    @Inject(method = "tame", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$tameXp(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            SpeechXpHandler.onTame(serverPlayer);
        }
    }
}
