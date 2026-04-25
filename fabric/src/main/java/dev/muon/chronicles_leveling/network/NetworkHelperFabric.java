package dev.muon.chronicles_leveling.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of {@link NetworkHelper}. All payloads exchanged
 * through this helper must implement {@link CustomPacketPayload}; the
 * registration side ({@code NetworkRegistrationFabric}) is responsible for
 * giving each one a {@code Type} + {@code StreamCodec}.
 */
public final class NetworkHelperFabric implements NetworkHelper {

    @Override
    public void sendToPlayer(ServerPlayer player, Object payload) {
        if (!(payload instanceof CustomPacketPayload p)) return;
        ServerPlayNetworking.send(player, p);
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, Object payload) {
        if (!(payload instanceof CustomPacketPayload p)) return;
        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, p);
        }
    }

    @Override
    public void sendToServer(Object payload) {
        if (!(payload instanceof CustomPacketPayload p)) return;
        ClientPlayNetworking.send(p);
    }
}
