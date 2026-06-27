package dev.muon.chronicles_leveling.client.enchant;

import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;

/**
 * Client-side cache of Arcane Insight's server-revealed would-be enchantments for the open vanilla enchanting
 * screen, keyed by container id so a stale packet from a closed menu is ignored.
 *
 * <p>The roll can't be replayed client-side: {@code ClientboundContainerSetDataPacket} writes data slots as
 * shorts, truncating the 32-bit enchantment seed to 16 bits, so the client never has the seed the server rolled
 * with. The full lists are therefore pushed from the server ({@code ArcaneInsightCluesPacket}). Holds no
 * client-only types, so it is harmless if ever loaded on a dedicated server. Accessed only on the client main
 * thread (packet handler marshals there; the screen reads while rendering).
 */
public final class ArcaneInsightClues {

    private ArcaneInsightClues() {}

    private static int containerId = -1;
    private static List<List<EnchantmentInstance>> slots = List.of();

    public static void accept(int container, List<List<EnchantmentInstance>> revealed) {
        containerId = container;
        slots = revealed;
    }

    public static List<EnchantmentInstance> get(int container, int slot) {
        if (container != containerId || slot < 0 || slot >= slots.size()) {
            return List.of();
        }
        return slots.get(slot);
    }
}
