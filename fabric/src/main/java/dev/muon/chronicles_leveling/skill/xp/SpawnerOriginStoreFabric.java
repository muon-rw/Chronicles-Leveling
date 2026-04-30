package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.attachment.FabricAttachments;
import net.minecraft.world.entity.Entity;

public final class SpawnerOriginStoreFabric implements SpawnerOriginStore {

    @Override
    public void mark(Entity entity) {
        entity.setAttached(FabricAttachments.SPAWNER_ORIGIN, Boolean.TRUE);
    }

    @Override
    public boolean isFromSpawner(Entity entity) {
        Boolean flag = entity.getAttached(FabricAttachments.SPAWNER_ORIGIN);
        return flag != null && flag;
    }
}
