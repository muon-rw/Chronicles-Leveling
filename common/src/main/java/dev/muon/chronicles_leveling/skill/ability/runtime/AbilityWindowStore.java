package dev.muon.chronicles_leveling.skill.ability.runtime;

import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-only, transient per-player timed combat-state windows; NOT a status-effect engine. It owns
 * only short (&lt;~3s), server-enforced windows the damage path must query this tick (i-frames, an armed
 * parry). Anything that must persist across relog, stack, or show an icon is a vanilla
 * {@code MobEffectInstance}, not an {@link ActiveWindow}.
 *
 * <p>Windows are keyed by game-time end-tick (absolute, monotonic) so they need no per-tick decrement;
 * the {@link #tick} driver only drops expired windows and GCs empty lists. {@link #open} REPLACES a
 * same-kind window (refresh, never stack), so two casts can't union their durations.
 */
public final class AbilityWindowStore {

    private AbilityWindowStore() {}

    /** A single active window: which kind, and the game-time tick it expires at. */
    public record ActiveWindow(WindowKind kind, long endTick) {}

    /**
     * The server-enforced window kinds. Deliberately tiny for exhaustiveness; a genuinely novel kind is
     * the one place an addon might want a core permit (promotable to a kind-id registry; see the design doc).
     */
    public enum WindowKind {
        /** All incoming damage negated for the duration (Dash). */
        IFRAME,
        /** The next incoming hit is consumed/negated once (Bulwark parry). */
        PARRY_ARMED,
        /** Every melee hit crits; a hit that would already crit deals bonus true damage (Master's Focus). */
        MASTERS_FOCUS,
        /** Mined ores drop their smelted result (Mining: Smelter's Touch). */
        SMELTERS_TOUCH,
        /** Pickaxe-mineable blocks break instantly (Mining: Superbreaker). */
        SUPERBREAKER,
        /** Nearby ores are highlighted through walls (Mining: Vein Sense). */
        VEIN_SIGHT
    }

    private static final Map<UUID, List<ActiveWindow>> ACTIVE = new HashMap<>();

    /** Opens (or refreshes) a window of the given kind for {@code durationTicks} from now. */
    public static void open(ServerPlayer player, WindowKind kind, int durationTicks) {
        long end = player.level().getGameTime() + durationTicks;
        List<ActiveWindow> windows = ACTIVE.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        windows.removeIf(w -> w.kind() == kind);   // refresh, never stack
        windows.add(new ActiveWindow(kind, end));
        NetworkDispatcher.sendAbilityWindows(player);
    }

    /** A snapshot of the player's non-expired windows (server side), for the client-sync packet. */
    public static List<ActiveWindow> activeWindowsOf(ServerPlayer player) {
        List<ActiveWindow> windows = ACTIVE.get(player.getUUID());
        if (windows == null || windows.isEmpty()) {
            return List.of();
        }
        long now = player.level().getGameTime();
        List<ActiveWindow> snapshot = new ArrayList<>();
        for (ActiveWindow w : windows) {
            if (w.endTick() > now) {
                snapshot.add(w);
            }
        }
        return snapshot;
    }

    /** Whether the player currently has a non-expired window of the given kind. */
    public static boolean isActive(ServerPlayer player, WindowKind kind) {
        List<ActiveWindow> windows = ACTIVE.get(player.getUUID());
        if (windows == null) {
            return false;
        }
        long now = player.level().getGameTime();
        for (ActiveWindow w : windows) {
            if (w.kind() == kind && w.endTick() > now) {
                return true;
            }
        }
        return false;
    }

    /** One-shot: if a non-expired window of the kind exists, removes it and returns true (e.g. parry). */
    public static boolean consume(ServerPlayer player, WindowKind kind) {
        List<ActiveWindow> windows = ACTIVE.get(player.getUUID());
        if (windows == null) {
            return false;
        }
        long now = player.level().getGameTime();
        for (Iterator<ActiveWindow> it = windows.iterator(); it.hasNext(); ) {
            ActiveWindow w = it.next();
            if (w.kind() == kind && w.endTick() > now) {
                it.remove();
                NetworkDispatcher.sendAbilityWindows(player);
                return true;
            }
        }
        return false;
    }

    /** Drops a player's windows on logout (the tick driver only sees online players). */
    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    /** Per-tick: expire elapsed windows for online players and GC empties; belt-and-suspenders drop offline UUIDs. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<ActiveWindow> windows = ACTIVE.get(player.getUUID());
            if (windows == null) {
                continue;
            }
            long now = player.level().getGameTime();
            windows.removeIf(w -> w.endTick() <= now);
            if (windows.isEmpty()) {
                ACTIVE.remove(player.getUUID());
            }
        }
        ACTIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }
}
