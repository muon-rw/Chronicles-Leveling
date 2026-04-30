package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Decorator that appends a level suffix to a player's nameplate component.
 *
 * <p>CL owns player nameplate rendering on both loaders. Dynamic-Difficulty
 * detects CL at startup and skips its own player injection (mob injection is
 * unaffected), so the two never double up regardless of DD's
 * {@code injectLevelIntoPlayers} setting.
 *
 * <p>Wired from a loader-specific event/mixin — see
 * {@code ClientEventsNeoforge#onRenderNameTag} on NeoForge and
 * {@code mixin.EntityRendererMixin} on Fabric.
 */
public final class PlayerNameplateRenderer {

    private PlayerNameplateRenderer() {}

    public static boolean shouldDecorate() {
        return Configs.STATS.injectLevelIntoOwnNameplate.get();
    }

    /**
     * Returns the original component with a level suffix appended, or the
     * original unchanged if decoration is disabled. Always safe to call.
     */
    public static Component decorate(Component original, Player player) {
        if (!shouldDecorate()) return original;
        int level = PlayerLevelManager.getLevel(player);
        if (level <= 0) return original;

        MutableComponent levelSuffix = Component.literal(" ")
                .append(Component.translatable("chronicles_leveling.nameplate.level", level))
                .withStyle(style -> style.withColor(getLevelColor(Minecraft.getInstance().player, player)));

        return original.copy().append(levelSuffix);
    }

    /**
     * Color the level suffix relative to the local viewer's level. Mirrors
     * Dynamic-Difficulty's scheme so the visual is consistent whether DD is
     * present or not. ARGB (0xAARRGGBB).
     */
    private static int getLevelColor(Player viewer, Player target) {
        int viewerLevel = viewer != null ? PlayerLevelManager.getLevel(viewer) : 0;
        int targetLevel = PlayerLevelManager.getLevel(target);
        if (viewerLevel > 0) {
            int diff = targetLevel - viewerLevel;
            if (diff > 10) return 0xFFFF0000;
            if (diff > -5) return 0xFFFFFF00;
            return 0xFF00FF00;
        }
        if (targetLevel < 8) return 0xFF00FF00;
        if (targetLevel <= 19) return 0xFFFFFF00;
        return 0xFFFF0000;
    }
}
