package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
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
 * <p>Reads are cheap — they hit the attachment directly. Writes always sync;
 * skip the network if you're calling from a hot path that mutates many players.
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

    /**
     * Writes new state. Sync to the owning client rides the loader's attachment
     * sync — we don't fire a separate packet. DD is poked when present so its
     * display level refreshes immediately rather than waiting on its own
     * tick-based fallback.
     */
    public static void set(ServerPlayer player, PlayerLevelData data) {
        Services.PLATFORM.getPlayerLevelStore().set(player, data);
        DynamicDifficultyCompat.requestPlayerLevelUpdate(player);
    }

    /**
     * Adds XP, awarding stat points and rolling over remainder for each level
     * the player crosses. Negative deltas are rejected — call {@link #set}
     * directly if you need to wipe XP.
     */
    public static void addXp(ServerPlayer player, int xpDelta) {
        if (xpDelta <= 0) return;

        PlayerLevelData data = get(player);
        int level = data.level();
        int xp = data.xp() + xpDelta;
        int unspent = data.unspentPoints();
        int pointsPerLevel = Configs.SYNC.pointsPerLevel.get();

        int rung = LevelingCurve.xpToNext(level);
        while (xp >= rung) {
            xp -= rung;
            level += 1;
            unspent += pointsPerLevel;
            rung = LevelingCurve.xpToNext(level);
        }

        if (level != data.level()) {
            ChroniclesLeveling.LOG.debug("Player {} leveled up: {} -> {} (+{} unspent)",
                    player.getName().getString(), data.level(), level, level - data.level());
        }

        set(player, new PlayerLevelData(level, xp, unspent));
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
