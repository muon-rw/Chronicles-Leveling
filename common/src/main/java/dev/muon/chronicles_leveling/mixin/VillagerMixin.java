package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.social.SpeechTradeHandler;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Speech trade perks scoped to villagers: Haggler (sell-side, on open), Master Negotiator (XP), Reputation (restock). */
@Mixin(value = Villager.class, remap = false)
public abstract class VillagerMixin {

    @Shadow private long lastRestockGameTime;
    @Shadow private int numberOfRestocksToday;

    /** One vanilla restock interval; gates the perk-added restocks to vanilla's cadence. */
    @Unique private static final long RESTOCK_INTERVAL_TICKS = 2400L;

    @Inject(method = "updateSpecialPrices", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$hagglerDiscount(Player player, CallbackInfo ci) {
        SpeechTradeHandler.applyHagglerDiscount((Villager) (Object) this, player);
    }

    @ModifyExpressionValue(method = "rewardTradeXp",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffer;getXp()I"),
            remap = false)
    private int chronicles_leveling$masterNegotiator(int xp) {
        return SpeechTradeHandler.boostVillagerXp((Villager) (Object) this, xp);
    }

    @Inject(method = "rewardTradeXp", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$resyncTradeProgress(MerchantOffer offer, CallbackInfo ci) {
        SpeechTradeHandler.resyncTradeProgress((Villager) (Object) this);
    }

    @ModifyReturnValue(method = "allowedToRestock", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$reputationRestock(boolean original) {
        if (original) {
            return true;
        }
        Villager self = (Villager) (Object) this;
        return numberOfRestocksToday < Configs.SKILLS.speech.reputationRestockLimit.get()
                && self.level().getGameTime() > lastRestockGameTime + RESTOCK_INTERVAL_TICKS
                && SpeechTradeHandler.hasNearbyReputableTrader(self);
    }
}
