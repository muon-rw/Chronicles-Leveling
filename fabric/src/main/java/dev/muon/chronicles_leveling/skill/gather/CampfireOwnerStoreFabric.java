package dev.muon.chronicles_leveling.skill.gather;

import dev.muon.chronicles_leveling.attachment.FabricAttachments;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class CampfireOwnerStoreFabric implements CampfireOwnerStore {

    @Override
    public CampfireOwnerData get(BlockEntity be) {
        CampfireOwnerData data = be.getAttached(FabricAttachments.CAMPFIRE_OWNER);
        return data != null ? data : CampfireOwnerData.DEFAULT;
    }

    @Override
    public void set(BlockEntity be, CampfireOwnerData data) {
        be.setAttached(FabricAttachments.CAMPFIRE_OWNER, data);
    }
}
