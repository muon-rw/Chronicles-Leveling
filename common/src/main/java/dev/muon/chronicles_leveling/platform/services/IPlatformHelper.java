package dev.muon.chronicles_leveling.platform.services;

import dev.muon.chronicles_leveling.level.PlayerLevelStore;
import dev.muon.chronicles_leveling.network.NetworkHelper;

public interface IPlatformHelper {

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /**
     * Loader-specific accessor for the player's level/xp/points attachment.
     * On NeoForge this is backed by a synced AttachmentType; on Fabric it's a
     * non-synced AttachmentRegistry entry that we sync manually for join-timing
     * reasons (mirroring Dynamic-Difficulty's choice).
     */
    PlayerLevelStore getPlayerLevelStore();

    /**
     * Loader-specific networking adapter for sending packets to clients.
     * Mirrors {@code dev.muon.dynamic_difficulty.platform.NetworkHelper}.
     */
    NetworkHelper getNetworkHelper();
}
