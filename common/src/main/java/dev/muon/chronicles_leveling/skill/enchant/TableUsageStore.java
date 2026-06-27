package dev.muon.chronicles_leveling.skill.enchant;

import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-abstracted accessor for the per-player {@link TableUsageData} attachment (persistent, not synced;
 * server-side state for Wizard's Study). Mirrors {@code PlayerSkillStore}.
 */
public interface TableUsageStore {

    /** Reads the current usage record, defaulting to {@link TableUsageData#DEFAULT}. */
    TableUsageData get(ServerPlayer player);

    /** Writes the record; the underlying attachment persists with the player's NBT. */
    void set(ServerPlayer player, TableUsageData data);
}
