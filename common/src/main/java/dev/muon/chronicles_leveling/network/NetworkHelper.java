package dev.muon.chronicles_leveling.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-specific packet send adapter. Mirrors DD's {@code NetworkHelper}.
 *
 * <p>Implementations are responsible for the actual wire format / channel
 * registration; common code only ever asks them to "send this typed payload to
 * this player". {@code Object} is used because the concrete payload base class
 * differs between loaders (Fabric: {@code CustomPayload}, NeoForge:
 * {@code CustomPacketPayload}).
 */
public interface NetworkHelper {

    void sendToPlayer(ServerPlayer player, Object payload);

    void sendToAllPlayers(MinecraftServer server, Object payload);

    void sendToServer(Object payload);
}
