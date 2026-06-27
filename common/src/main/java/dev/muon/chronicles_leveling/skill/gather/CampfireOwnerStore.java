package dev.muon.chronicles_leveling.skill.gather;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Loader-abstracted accessor for the campfire owner BE attachment that records which player placed each cooking slot's
 * food (for Gardener's Infusion crediting). Backed by the platform attachment system, so persistence rides the BE's
 * normal save/load with no custom NBT mixin. Mirrors {@link dev.muon.chronicles_leveling.skill.xp.BrewingStationStore}.
 */
public interface CampfireOwnerStore {

    /** Reads the current state, defaulting to {@link CampfireOwnerData#DEFAULT}. */
    CampfireOwnerData get(BlockEntity be);

    /** Writes the state; the underlying attachment marks the BE dirty so vanilla saves it. */
    void set(BlockEntity be, CampfireOwnerData data);
}
