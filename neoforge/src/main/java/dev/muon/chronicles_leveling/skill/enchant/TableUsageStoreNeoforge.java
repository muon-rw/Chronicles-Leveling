package dev.muon.chronicles_leveling.skill.enchant;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import net.minecraft.server.level.ServerPlayer;

public final class TableUsageStoreNeoforge implements TableUsageStore {

    @Override
    public TableUsageData get(ServerPlayer player) {
        return player.getData(NeoforgeAttachments.TABLE_USAGE);
    }

    @Override
    public void set(ServerPlayer player, TableUsageData data) {
        player.setData(NeoforgeAttachments.TABLE_USAGE, data);
    }
}
