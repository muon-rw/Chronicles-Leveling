package dev.muon.chronicles_leveling.platform.services;

import dev.muon.chronicles_leveling.level.PlayerLevelStore;
import dev.muon.chronicles_leveling.network.NetworkHelper;
import dev.muon.chronicles_leveling.skill.PlayerSkillStore;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.Optional;

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
     * Loader-specific accessor for the player's skill levels + xp attachment.
     * Backed by a synced AttachmentType on both loaders so the owning client
     * always reflects the server-authoritative state.
     */
    PlayerSkillStore getPlayerSkillStore();

    /**
     * Loader-specific networking adapter for sending packets to clients.
     * Mirrors {@code dev.muon.dynamic_difficulty.platform.NetworkHelper}.
     */
    NetworkHelper getNetworkHelper();

    /**
     * Returns the percent-display scale factor for the given attribute, if the
     * loader natively marks it as a percentage attribute. Used as a fallback
     * for percent rendering when Dynamic-Tooltips isn't loaded.
     *
     * <p>NeoForge: detects {@code net.neoforged.neoforge.common.PercentageAttribute};
     * Fabric: returns empty (no native equivalent).
     */
    default Optional<Double>  percentScaleForAttribute(Holder<Attribute> holder) {
        return Optional.empty();
    }
}
