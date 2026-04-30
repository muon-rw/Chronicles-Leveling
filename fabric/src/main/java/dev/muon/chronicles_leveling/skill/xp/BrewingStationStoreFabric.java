package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.attachment.FabricAttachments;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BrewingStationStoreFabric implements BrewingStationStore {

    @Override
    public BrewingStationData get(BlockEntity be) {
        BrewingStationData data = be.getAttached(FabricAttachments.BREWING_STATION);
        return data != null ? data : BrewingStationData.DEFAULT;
    }

    @Override
    public void set(BlockEntity be, BrewingStationData data) {
        be.setAttached(FabricAttachments.BREWING_STATION, data);
    }
}
