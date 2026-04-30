package dev.muon.chronicles_leveling.skill.xp;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Loader-abstracted accessor for the brewing-stand BE attachment that holds
 * the per-slot freshly-brewed flags. Implementations live in the loader
 * modules and back this with the platform attachment system, so persistence
 * is driven by the BE's normal save/load — no custom NBT mixin required.
 *
 * <p>Mirrors {@link dev.muon.chronicles_leveling.skill.PlayerSkillStore}.
 */
public interface BrewingStationStore {

    /** Reads the current state, defaulting to {@link BrewingStationData#DEFAULT}. */
    BrewingStationData get(BlockEntity be);

    /** Writes the state; the underlying attachment marks the BE dirty so vanilla saves it. */
    void set(BlockEntity be, BrewingStationData data);
}
