package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Decorator that appends a level suffix to a player's nameplate component.
 *
 * <p>Single config gate (no awareness of DD). When DD is loaded alongside, the
 * intended setup is to disable DD's player nameplate injection (DD's
 * {@code injectLevelIntoPlayers} client config) so the two don't double up;
 * the dedup logic lives outside this class on purpose.
 *
 * <p>Hooked from a loader-specific mixin/event — see the {@code mixin.client.*}
 * package on each loader.
 */
public final class PlayerNameplateRenderer {

    private PlayerNameplateRenderer() {}

    public static boolean shouldDecorate() {
        return Configs.SYNC.injectLevelIntoOwnNameplate.get();
    }

    /**
     * Returns the original component with a level suffix appended, or the
     * original unchanged if decoration is disabled. Always safe to call.
     */
    public static Component decorate(Component original, Player player) {
        if (!shouldDecorate()) return original;
        int level = PlayerLevelManager.getLevel(player);
        if (level <= 0) return original;

        MutableComponent out = original.copy();
        out.append(Component.literal(" "));
        out.append(Component.translatable("chronicles_leveling.nameplate.level", level));
        return out;
    }
}
