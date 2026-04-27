package dev.muon.chronicles_leveling.skill;

import net.minecraft.world.entity.player.Player;

/**
 * Loader-abstracted accessor for {@link PlayerSkillData}. Implementations live
 * in the {@code fabric}/{@code neoforge} modules and back this with the
 * loader's attachment system.
 *
 * <p>Mirrors {@link dev.muon.chronicles_leveling.level.PlayerLevelStore} —
 * persistence + sync are handled by the underlying attachment.
 */
public interface PlayerSkillStore {

    /** Returns the player's skill state, or {@link PlayerSkillData#DEFAULT} if unset. */
    PlayerSkillData get(Player player);

    /** Writes the player's skill state. The attachment auto-syncs to the owning client. */
    void set(Player player, PlayerSkillData data);

    /** Whether the player has a non-default record stored. */
    boolean has(Player player);
}
