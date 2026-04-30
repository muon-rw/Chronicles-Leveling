package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.JumpXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grants the configured per-jump acrobatics XP. NeoForge has
 * {@code LivingEvent.LivingJumpEvent} for the same purpose; Fabric doesn't
 * ship a jump event so we mixin directly into {@link LivingEntity#jumpFromGround}.
 *
 * <p>Server-side gate keeps client prediction from double-counting: server
 * authoritatively decides whether the jump happened at all, and only the
 * server tracks XP.
 */
@Mixin(LivingEntity.class)
public class PlayerJumpMixin {

    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void chronicles_leveling$grantAcrobaticsXp(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player) {
            JumpXpHandler.onJump(player);
        }
    }
}
