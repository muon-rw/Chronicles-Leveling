package dev.muon.chronicles_leveling.level;

import net.minecraft.world.entity.player.Player;

public final class PlayerLevelStoreFabric implements PlayerLevelStore {

    @Override
    public PlayerLevelData get(Player player) {
        PlayerLevelData data = player.getAttached(PlayerLevelAttachmentFabric.LEVEL);
        return data != null ? data : PlayerLevelData.DEFAULT;
    }

    @Override
    public void set(Player player, PlayerLevelData data) {
        player.setAttached(PlayerLevelAttachmentFabric.LEVEL, data);
    }

    @Override
    public boolean has(Player player) {
        return player.hasAttached(PlayerLevelAttachmentFabric.LEVEL);
    }
}
