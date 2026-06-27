package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.muon.chronicles_leveling.skill.social.SpeechTradeHandler;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Speech trade perks at trade completion: Silver Tongue (skip stock) and Haggler buy-side (bonus emeralds). */
@Mixin(value = AbstractVillager.class, remap = false)
public abstract class AbstractVillagerMixin {

    @WrapWithCondition(method = "notifyTrade",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffer;increaseUses()V"),
            remap = false)
    private boolean chronicles_leveling$silverTongue(MerchantOffer offer) {
        return SpeechTradeHandler.consumesStock((AbstractVillager) (Object) this);
    }

    @Inject(method = "notifyTrade", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$hagglerBonus(MerchantOffer offer, CallbackInfo ci) {
        SpeechTradeHandler.applyHagglerBonus((AbstractVillager) (Object) this, offer);
    }
}
