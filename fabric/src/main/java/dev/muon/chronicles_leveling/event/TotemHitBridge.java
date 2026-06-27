package dev.muon.chronicles_leveling.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-thread state spanning one {@code hurtServer} call, bridging seams that cannot share
 * locals: {@code PlayerMixinFabric} stashes a lethal hit's post-mitigation amount, the
 * {@code LivingEntityMixinFabric} totem wrap consumes it (firing victim procs on a save) and marks
 * the save, and the {@code AFTER_DAMAGE} taken-XP handler consumes the mark to skip the hit.
 */
public final class TotemHitBridge {

    private TotemHitBridge() {}

    private static final Map<Integer, Float> PENDING_LETHAL = new HashMap<>();
    private static final Set<Integer> TOTEM_SAVED = new HashSet<>();

    public static void stashLethal(int entityId, float postMitigationAmount) {
        PENDING_LETHAL.put(entityId, postMitigationAmount);
    }

    public static float consumeLethal(int entityId) {
        Float amount = PENDING_LETHAL.remove(entityId);
        return amount == null ? 0f : amount;
    }

    public static void markSaved(int entityId) {
        TOTEM_SAVED.add(entityId);
    }

    public static boolean consumeSaved(int entityId) {
        return TOTEM_SAVED.remove(entityId);
    }
}
