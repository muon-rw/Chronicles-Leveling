package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.skill.social.SpeechTradeHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Speech trade enrichment, applied as the result slot is (re)assembled so it lands on BOTH cursor pickup and
 * shift-click mass trades (the on-take seam misses shift-click, which empties the result before {@code onTake} runs).
 * Enchanted Trader + Power Broker enrich the bought item server-side; Master Negotiator boosts the {@code futureXp}
 * preview on both sides so the trade screen's pending-XP segment reflects the bonus live.
 */
@Mixin(value = MerchantContainer.class, remap = false)
public abstract class MerchantContainerMixin {

    @Shadow @Final private Merchant merchant;
    @Shadow private int futureXp;

    @Inject(method = "updateSellItem", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$enrichResult(CallbackInfo ci) {
        MerchantContainer self = (MerchantContainer) (Object) this;
        MerchantOffer offer = self.getActiveOffer();
        if (offer == null) {
            return;
        }
        Player trader = this.merchant.getTradingPlayer();
        if (this.futureXp > 0) {
            this.futureXp = SpeechTradeHandler.boostFutureXp(trader, this.futureXp);
        }
        if (trader instanceof ServerPlayer player) {
            SpeechTradeHandler.enchantOfferResult(player, self.getItem(2), offer);
        }
    }
}
