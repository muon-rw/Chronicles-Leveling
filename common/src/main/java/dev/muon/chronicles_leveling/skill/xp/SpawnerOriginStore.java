package dev.muon.chronicles_leveling.skill.xp;

import net.minecraft.world.entity.Entity;

/**
 * Loader-abstracted accessor for the per-entity "spawned by a mob spawner"
 * flag used by the skill XP router's spawner-multiplier path. Backed by the
 * platform attachment system so the flag persists with the entity NBT —
 * a spawner-spawned mob that survives a chunk unload or server restart still
 * counts as spawner-spawned when later killed.
 *
 * <p>Single-bit data, so the API is just mark/check rather than the
 * get/set pair the multi-field stores use.
 */
public interface SpawnerOriginStore {

    /** Tags the entity as spawner-spawned. Idempotent. */
    void mark(Entity entity);

    /** True iff the entity has been tagged via {@link #mark}. */
    boolean isFromSpawner(Entity entity);
}
