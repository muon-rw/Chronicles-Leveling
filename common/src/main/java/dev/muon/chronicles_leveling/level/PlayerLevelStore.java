package dev.muon.chronicles_leveling.level;

import net.minecraft.world.entity.player.Player;

/**
 * Loader-abstracted accessor for {@link PlayerLevelData}. Implementations live
 * in the {@code fabric}/{@code neoforge} modules and back this with the
 * loader's attachment system.
 *
 * <p>Mirrors the shape of {@code dev.muon.dynamic_difficulty.platform.LevelAttachmentHelper};
 * we deliberately keep this independent of DD so Chronicles can ship its own
 * leveling state regardless of whether DD is present.
 */
public interface PlayerLevelStore {

    /** Returns the player's current data, or {@link PlayerLevelData#DEFAULT} if unset. */
    PlayerLevelData get(Player player);

    /**
     * Writes the player's data. Persistence + (on NeoForge) auto-sync are handled
     * by the underlying attachment; on Fabric callers should also dispatch a
     * sync packet via {@code NetworkDispatcher}.
     */
    void set(Player player, PlayerLevelData data);

    /** Whether the player has a non-default record stored. */
    boolean has(Player player);
}
