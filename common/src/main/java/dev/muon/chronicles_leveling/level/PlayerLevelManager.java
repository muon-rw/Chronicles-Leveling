package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.compat.DynamicDifficultyCompat;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side facade for reading + writing player leveling state.
 *
 * <p>All routes that mutate level/points go through this class so we have
 * exactly one place to:
 * <ul>
 *   <li>persist via the loader-specific {@link PlayerLevelStore}</li>
 *   <li>dispatch sync packets to the owning client</li>
 *   <li>nudge Dynamic-Difficulty to refresh display levels when integrated</li>
 * </ul>
 *
 * <p>Leveling is opt-in: the player spends vanilla XP — the same pool they'd
 * burn on enchanting — by clicking the {@code +} on the screen, which routes
 * to {@link #tryLevelUp}. The mod doesn't bank a separate XP counter; per-skill
 * progress is tracked separately by {@code PlayerSkillManager}.
 */
public final class PlayerLevelManager {

    private PlayerLevelManager() {}

    public static PlayerLevelData get(Player player) {
        return Services.PLATFORM.getPlayerLevelStore().get(player);
    }

    public static int getLevel(Player player) {
        return get(player).level();
    }

    public static int getUnspentPoints(Player player) {
        return get(player).unspentPoints();
    }

    public static void set(ServerPlayer player, PlayerLevelData data) {
        Services.PLATFORM.getPlayerLevelStore().set(player, data);
        DynamicDifficultyCompat.requestPlayerLevelUpdate(player);
    }

    /**
     * Spends the rung's vanilla-XP cost, raises the level by 1, and credits {@link
     * Configs#SYNC ConfigSync#pointsPerLevel} unspent points. No-op if the
     * player can't afford it.
     *
     * @return {@code true} if a level-up actually happened
     */
    public static boolean tryLevelUp(ServerPlayer player) {
        PlayerLevelData data = get(player);
        int maxLevel = Configs.SYNC.maxLevel.get();
        if (maxLevel > 0 && data.level() >= maxLevel) return false;

        int cost = LevelingCurve.xpToNext(data.level());
        if (VanillaXp.availableExperiencePoints(player) < cost) return false;

        // Spend the rung cost out of vanilla XP so the player's enchanting pool drops.
        // giveExperiencePoints accepts negative deltas and drains progress + levels in
        // the right order — the standard XP-update packet syncs the new state to the client.
        player.giveExperiencePoints(-cost);

        int pointsPerLevel = Configs.SYNC.pointsPerLevel.get();
        set(player, new PlayerLevelData(
                data.level() + 1,
                data.unspentPoints() + pointsPerLevel,
                data.allocations()
        ));
        return true;
    }

}
