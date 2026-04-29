package dev.muon.chronicles_leveling.network;

import dev.muon.chronicles_leveling.network.message.AllocateStatPacket;
import dev.muon.chronicles_leveling.network.message.LevelUpPacket;
import dev.muon.chronicles_leveling.network.message.ResetStatPacket;
import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.world.InteractionHand;

/**
 * Common-side packet entry points. Internal callers go through here so we have
 * one place to find every send-site and one place to add log/metrics later.
 *
 * <p>Player leveling state itself rides the loader's attachment sync (NeoForge
 * built-in, Fabric {@code syncWith}); this class only carries the input
 * packets where the client is asking the server to do something.
 */
public final class NetworkDispatcher {

    private NetworkDispatcher() {}

    private static NetworkHelper helper() {
        return Services.PLATFORM.getNetworkHelper();
    }

    /** Client → server: "I clicked + on stat X". */
    public static void sendAllocateStat(String statId) {
        helper().sendToServer(new AllocateStatPacket(statId));
    }

    /** Client → server: "I clicked + on Level". */
    public static void sendLevelUp() {
        helper().sendToServer(LevelUpPacket.INSTANCE);
    }

    /** Client → server: "I confirmed a reset of stat X using the orb in this hand". */
    public static void sendResetStat(String statId, InteractionHand hand) {
        helper().sendToServer(new ResetStatPacket(statId, hand));
    }
}
