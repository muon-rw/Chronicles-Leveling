package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side mirror of the local player's active ability windows, pushed from the server
 * ({@code AbilityWindowsPacket}) since {@link AbilityWindowStore} is server-only. Read by client-predicted,
 * window-gated effects (e.g. Superbreaker's instant-break mixin). Holds no client-only types, so it is
 * harmless if ever loaded on a dedicated server; written/read on the client main thread.
 */
public final class AbilityWindowStoreClient {

    private AbilityWindowStoreClient() {}

    private static final Map<AbilityWindowStore.WindowKind, Long> ENDS = new EnumMap<>(AbilityWindowStore.WindowKind.class);

    public static void accept(List<AbilityWindowStore.ActiveWindow> windows) {
        ENDS.clear();
        for (AbilityWindowStore.ActiveWindow window : windows) {
            ENDS.put(window.kind(), window.endTick());
        }
    }

    public static boolean isActive(AbilityWindowStore.WindowKind kind, long gameTime) {
        Long end = ENDS.get(kind);
        return end != null && end > gameTime;
    }
}
