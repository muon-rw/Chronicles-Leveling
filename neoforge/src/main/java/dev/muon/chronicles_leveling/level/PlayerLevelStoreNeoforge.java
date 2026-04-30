package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import net.minecraft.world.entity.player.Player;

public final class PlayerLevelStoreNeoforge implements PlayerLevelStore {

    @Override
    public PlayerLevelData get(Player player) {
        return player.getData(NeoforgeAttachments.PLAYER_LEVEL);
    }

    @Override
    public void set(Player player, PlayerLevelData data) {
        player.setData(NeoforgeAttachments.PLAYER_LEVEL, data);
    }

    @Override
    public boolean has(Player player) {
        return player.getExistingDataOrNull(NeoforgeAttachments.PLAYER_LEVEL) != null;
    }
}
