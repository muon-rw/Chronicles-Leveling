package dev.muon.chronicles_leveling.level;

import net.minecraft.world.entity.player.Player;

public final class PlayerLevelStoreNeoforge implements PlayerLevelStore {

    @Override
    public PlayerLevelData get(Player player) {
        return player.getData(PlayerLevelAttachmentNeoforge.LEVEL);
    }

    @Override
    public void set(Player player, PlayerLevelData data) {
        player.setData(PlayerLevelAttachmentNeoforge.LEVEL, data);
    }

    @Override
    public boolean has(Player player) {
        return player.getExistingDataOrNull(PlayerLevelAttachmentNeoforge.LEVEL) != null;
    }
}
