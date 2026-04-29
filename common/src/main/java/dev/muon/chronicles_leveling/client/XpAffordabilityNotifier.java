package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.LevelingCurve;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.level.VanillaXp;
import dev.muon.chronicles_leveling.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * Plays {@code chronicles_leveling:level_up} the moment the player crosses
 * into "can afford the next mod-level rung" territory.
 *
 * <p>Polled from the client tick. A listener on the XP-change packet would be
 * more event-driven, but vanilla doesn't expose a clean cross-loader hook —
 * the {@code /xp} command, enchanting, mob kills, and player respawn all
 * write XP through different paths. Per-tick polling against the synced
 * {@link LocalPlayer} state catches every path with a one-tick worst case.
 */
public final class XpAffordabilityNotifier {

    /**
     * Tri-state: null until the first observed tick (so we don't spuriously play
     * the sound on screen-open / world-join), then tracks the last known
     * affordability so transitions {@code false → true} fire once.
     */
    private static Boolean wasAffordable = null;

    private XpAffordabilityNotifier() {}

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            wasAffordable = null;
            return;
        }
        boolean affordable = computeAffordable(player);
        if (Boolean.FALSE.equals(wasAffordable) && affordable) {
            playLevelUpSound();
        }
        wasAffordable = affordable;
    }

    private static boolean computeAffordable(LocalPlayer player) {
        PlayerLevelData data = PlayerLevelManager.get(player);
        int maxLevel = Configs.SYNC.maxLevel.get();
        if (maxLevel > 0 && data.level() >= maxLevel) return false;
        return VanillaXp.availableExperiencePoints(player) >= LevelingCurve.xpToNext(data.level());
    }

    private static void playLevelUpSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LEVEL_UP.value(), 1.0f));
    }
}
