package dev.muon.chronicles_leveling.skill.gather;

import dev.muon.chronicles_leveling.attachment.FabricAttachments;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class FurnaceOwnerStoreFabric implements FurnaceOwnerStore {

    @Override
    public FurnaceOwnerData get(BlockEntity be) {
        FurnaceOwnerData data = be.getAttached(FabricAttachments.FURNACE_OWNER);
        return data != null ? data : FurnaceOwnerData.DEFAULT;
    }

    @Override
    public void set(BlockEntity be, FurnaceOwnerData data) {
        be.setAttached(FabricAttachments.FURNACE_OWNER, data);
    }
}
