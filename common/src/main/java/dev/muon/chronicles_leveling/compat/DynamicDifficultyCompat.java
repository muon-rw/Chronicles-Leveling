package dev.muon.chronicles_leveling.compat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges Chronicles' player level into Dynamic-Difficulty's {@link PlayerLevelProvider}. We compile
 * against DD's API directly; runtime presence is gated by {@code isModLoaded} so the integration stays
 * optional even on builds where DD is {@code compileOnly}. Because JVM class loading is lazy, the inner
 * {@link Provider} isn't loaded until {@link #init()} instantiates it behind that gate, so removing DD
 * from the runtime classpath does not produce a {@code NoClassDefFoundError} on startup.
 */
public final class DynamicDifficultyCompat {

    private static final String MOD_ID = "dynamic_difficulty";

    private static boolean initialized = false;
    private static boolean active = false;

    private DynamicDifficultyCompat() {}

    /** One-time setup. Safe to call when DD is absent; it just returns. */
    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!Services.PLATFORM.isModLoaded(MOD_ID)) {
            ChroniclesLeveling.LOG.debug("Dynamic-Difficulty not loaded, skipping integration");
            return;
        }

        LevelingAPI.registerPlayerLevelProvider(new Provider());
        active = true;
        ChroniclesLeveling.LOG.info("Registered Chronicles player level provider with Dynamic-Difficulty");
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Asks DD to recompute and resync this player's display level. No-op when DD isn't loaded; the
     * live attachment sync still propagates the data Chronicles itself reads.
     */
    public static void requestPlayerLevelUpdate(ServerPlayer player) {
        if (!active) return;
        PlayerLevelProvider.requestPlayerLevelUpdate(player);
    }

    /**
     * Static-nested rather than anonymous so its loading is visibly tied to the {@code new Provider()}
     * call site in {@link #init()}, not to enclosing-class load.
     */
    private static final class Provider implements PlayerLevelProvider {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public int getPlayerLevel(ServerPlayer player) {
            return PlayerLevelManager.getLevel(player);
        }

        @Override
        public int getDisplayPriority() {
            // Higher than DD's built-in playtime provider (-10) so Chronicles' level
            // wins under any HIGHEST_PRIORITY display strategy.
            return 100;
        }
    }
}
