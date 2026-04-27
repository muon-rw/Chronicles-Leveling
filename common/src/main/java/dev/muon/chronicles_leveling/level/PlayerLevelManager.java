package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.compat.DynamicDifficultyCompat;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side facade for reading + writing player leveling state.
 *
 * <p>All routes that mutate level/xp/points go through this class so we have
 * exactly one place to:
 * <ul>
 *   <li>persist via the loader-specific {@link PlayerLevelStore}</li>
 *   <li>dispatch sync packets to the owning client</li>
 *   <li>nudge Dynamic-Difficulty to refresh display levels when integrated</li>
 * </ul>
 *
 * <p>Leveling is opt-in: {@link #addXp} only banks XP; the player has to spend
 * it via {@link #tryLevelUp} to actually rise a rung. That keeps levels and
 * stat points in the same "click + to spend" loop on the screen.
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
     * Banks XP toward the next level. Negative deltas are rejected — call
     * {@link #set} directly if you need to wipe XP. Does <em>not</em> auto-level
     * the player; that's the player's choice via {@link #tryLevelUp}.
     */
    public static void addXp(ServerPlayer player, int xpDelta) {
        if (xpDelta <= 0) return;
        PlayerLevelData data = get(player);
        set(player, data.withXp(data.xp() + xpDelta));
    }

    /**
     * Spends the rung's XP cost, raises the level by 1, and credits {@link
     * Configs#SYNC ConfigSync#pointsPerLevel} unspent points. No-op if the
     * player can't afford it.
     *
     * @return {@code true} if a level-up actually happened
     */
    public static boolean tryLevelUp(ServerPlayer player) {
        PlayerLevelData data = get(player);
        int cost = LevelingCurve.xpToNext(data.level());
        if (data.xp() < cost) return false;

        int pointsPerLevel = Configs.SYNC.pointsPerLevel.get();
        set(player, new PlayerLevelData(
                data.level() + 1,
                data.xp() - cost,
                data.unspentPoints() + pointsPerLevel
        ));
        return true;
    }

    /**
     * Spends one point from the unspent pool. No-op if the player has none.
     * The actual attribute mutation is the caller's job — this only debits the bank.
     *
     * @return {@code true} if a point was successfully debited
     */
    public static boolean trySpendPoint(ServerPlayer player) {
        PlayerLevelData data = get(player);
        if (data.unspentPoints() <= 0) return false;
        set(player, data.withUnspentPoints(data.unspentPoints() - 1));
        return true;
    }
}
