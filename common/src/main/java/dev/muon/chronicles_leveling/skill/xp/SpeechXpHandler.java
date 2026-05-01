package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Map;

/**
 * The merchant XP attached to a {@link MerchantOffer} is the closest stable
 * proxy for trade tier (vanilla scales it 2 → 30 across novice → master), and
 * modded merchants pick whatever they want — so any "tier" interpretation
 * belongs in the config formula rather than baked-in here.
 */
public final class SpeechXpHandler {

    private SpeechXpHandler() {}

    public static void onTrade(ServerPlayer player, MerchantOffer offer) {
        if (offer == null) return;
        double xp = Configs.SKILLS.speech.xpPerTradeXp.evalSafe(
                Map.of('x', (double) offer.getXp()), 0.0);
        PlayerSkillManager.grantXp(player, Skills.SPEECH, xp);
    }
}
