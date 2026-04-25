package dev.muon.chronicles_leveling.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NetworkHelperNeoforge implements NetworkHelper {

    @Override
    public void sendToPlayer(ServerPlayer player, Object payload) {
        if (!(payload instanceof CustomPacketPayload p)) return;
        PacketDistributor.sendToPlayer(player, p);
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, Object payload) {
        if (!(payload instanceof CustomPacketPayload p)) return;
        PacketDistributor.sendToAllPlayers(p);
    }

    @Override
    public void sendToServer(Object payload) {
        if (!(payload instanceof CustomPacketPayload p)) return;
        // C2S lives in the client-only distributor in modern NeoForge — calling it
        // from the server would NoClassDefFoundError, so callers must already be
        // on the client (the only place that calls this is the level-up screen).
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(p);
    }
}
