package dev.muon.chronicles_leveling.skill.gather;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Loader-abstracted accessor for the furnace cook-owner BE attachment (who loaded the smelt), so Gardener's Infusion
 * can credit the cook when the result is assembled. Backed by the platform attachment system; persistence rides the
 * BE's normal save/load. Mirrors {@link dev.muon.chronicles_leveling.skill.xp.BrewingStationStore}.
 */
public interface FurnaceOwnerStore {

    /** Reads the current state, defaulting to {@link FurnaceOwnerData#DEFAULT}. */
    FurnaceOwnerData get(BlockEntity be);

    /** Writes the state; the underlying attachment marks the BE dirty so vanilla saves it. */
    void set(BlockEntity be, FurnaceOwnerData data);
}
