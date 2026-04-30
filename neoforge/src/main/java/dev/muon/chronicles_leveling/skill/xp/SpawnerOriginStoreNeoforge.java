package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import net.minecraft.world.entity.Entity;

public final class SpawnerOriginStoreNeoforge implements SpawnerOriginStore {

    @Override
    public void mark(Entity entity) {
        entity.setData(NeoforgeAttachments.SPAWNER_ORIGIN, Boolean.TRUE);
    }

    @Override
    public boolean isFromSpawner(Entity entity) {
        return entity.getData(NeoforgeAttachments.SPAWNER_ORIGIN);
    }
}
