package dev.muon.chronicles_leveling.client.enchant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Client-side cache of the player's most-used enchanting table (Wizard's Study), pushed from the server
 * ({@code WizardsStudyTablePacket}) because the usage attachment is server-only. The enchant-table renderer reads
 * this to glow that table's floating book for the owning player. Holds no client-only types, so it is harmless if
 * ever loaded on a dedicated server; written/read only on the client main thread.
 */
public final class WizardsStudyClient {

    private WizardsStudyClient() {}

    private static GlobalPos target;

    public static void accept(Optional<GlobalPos> mostUsed) {
        target = mostUsed.orElse(null);
    }

    public static boolean matches(BlockPos pos, ResourceKey<Level> dimension) {
        return target != null && target.pos().equals(pos) && target.dimension().equals(dimension);
    }

    public static boolean hasTargetIn(ResourceKey<Level> dimension) {
        return target != null && target.dimension().equals(dimension);
    }
}
