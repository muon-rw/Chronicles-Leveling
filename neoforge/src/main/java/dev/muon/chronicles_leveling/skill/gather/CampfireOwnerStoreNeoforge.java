package dev.muon.chronicles_leveling.skill.gather;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class CampfireOwnerStoreNeoforge implements CampfireOwnerStore {

    @Override
    public CampfireOwnerData get(BlockEntity be) {
        return be.getData(NeoforgeAttachments.CAMPFIRE_OWNER);
    }

    @Override
    public void set(BlockEntity be, CampfireOwnerData data) {
        be.setData(NeoforgeAttachments.CAMPFIRE_OWNER, data);
    }
}
