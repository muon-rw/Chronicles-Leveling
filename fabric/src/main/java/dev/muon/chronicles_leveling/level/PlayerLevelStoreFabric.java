package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.attachment.FabricAttachments;
import net.minecraft.world.entity.player.Player;

public final class PlayerLevelStoreFabric implements PlayerLevelStore {

    @Override
    public PlayerLevelData get(Player player) {
        PlayerLevelData data = player.getAttached(FabricAttachments.PLAYER_LEVEL);
        return data != null ? data : PlayerLevelData.DEFAULT;
    }

    @Override
    public void set(Player player, PlayerLevelData data) {
        player.setAttached(FabricAttachments.PLAYER_LEVEL, data);
    }

    @Override
    public boolean has(Player player) {
        return player.hasAttached(FabricAttachments.PLAYER_LEVEL);
    }
}
