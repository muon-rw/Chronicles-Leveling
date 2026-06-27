package dev.muon.chronicles_leveling.skill.gather;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class FurnaceOwnerStoreNeoforge implements FurnaceOwnerStore {

    @Override
    public FurnaceOwnerData get(BlockEntity be) {
        return be.getData(NeoforgeAttachments.FURNACE_OWNER);
    }

    @Override
    public void set(BlockEntity be, FurnaceOwnerData data) {
        be.setData(NeoforgeAttachments.FURNACE_OWNER, data);
    }
}
