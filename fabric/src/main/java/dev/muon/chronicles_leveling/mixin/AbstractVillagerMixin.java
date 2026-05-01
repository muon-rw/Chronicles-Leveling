package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.xp.SpeechXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla {@code notifyTrade} fires once per accepted trade after the offer's
 *  use count is bumped — exact granularity for Speech XP. */
@Mixin(value = AbstractVillager.class, remap = false)
public abstract class AbstractVillagerMixin {

    @Inject(method = "notifyTrade", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$grantSpeechXp(MerchantOffer offer, CallbackInfo ci) {
        AbstractVillager self = (AbstractVillager) (Object) this;
        if (self.getTradingPlayer() instanceof ServerPlayer player) {
            SpeechXpHandler.onTrade(player, offer);
        }
    }
}
