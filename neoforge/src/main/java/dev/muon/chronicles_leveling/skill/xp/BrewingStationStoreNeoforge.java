package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BrewingStationStoreNeoforge implements BrewingStationStore {

    @Override
    public BrewingStationData get(BlockEntity be) {
        return be.getData(NeoforgeAttachments.BREWING_STATION);
    }

    @Override
    public void set(BlockEntity be, BrewingStationData data) {
        be.setData(NeoforgeAttachments.BREWING_STATION, data);
    }
}
