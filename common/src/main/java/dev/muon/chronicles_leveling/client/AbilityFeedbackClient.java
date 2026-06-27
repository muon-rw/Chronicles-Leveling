package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.skill.ability.CastDenyReason;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * Client reaction to a denied cast ({@code CastFailedPacket}). Shows the action-bar message (built here from the
 * reason, so text/styling is client-side) and plays a local fizzle. The {@code abilityId} is the seam for future
 * per-ability local effects (a custom fizzle sound/particle). Holds no client-only types beyond the call boundary,
 * so it is harmless if ever loaded on a dedicated server; invoked on the client main thread.
 */
public final class AbilityFeedbackClient {

    private AbilityFeedbackClient() {}

    public static void onCastFailed(Identifier abilityId, CastDenyReason reason, Component message, int detail) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Component text = message != null ? message
                : reason.usesDetail()
                        ? Component.translatable(reason.translationKey(), detail)
                        : Component.translatable(reason.translationKey());
        mc.gui.setOverlayMessage(text.copy().withStyle(ChatFormatting.RED), false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 0.8f, 0.4f));
    }
}
