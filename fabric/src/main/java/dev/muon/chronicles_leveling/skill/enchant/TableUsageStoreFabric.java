package dev.muon.chronicles_leveling.skill.enchant;

import dev.muon.chronicles_leveling.attachment.FabricAttachments;
import net.minecraft.server.level.ServerPlayer;

public final class TableUsageStoreFabric implements TableUsageStore {

    @Override
    public TableUsageData get(ServerPlayer player) {
        TableUsageData data = player.getAttached(FabricAttachments.TABLE_USAGE);
        return data != null ? data : TableUsageData.DEFAULT;
    }

    @Override
    public void set(ServerPlayer player, TableUsageData data) {
        player.setAttached(FabricAttachments.TABLE_USAGE, data);
    }
}
