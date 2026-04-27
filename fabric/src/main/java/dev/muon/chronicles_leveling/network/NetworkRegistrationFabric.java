package dev.muon.chronicles_leveling.network;

import dev.muon.chronicles_leveling.network.message.AllocateStatPacket;
import dev.muon.chronicles_leveling.network.message.LevelUpPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Fabric-side network channel registration.
 *
 * <p>Only the C2S allocate-stat packet rides this layer; player level state
 * itself flows over Fabric's built-in attachment sync now that the timing
 * issues that justified manual sync have been patched upstream.
 */
public final class NetworkRegistrationFabric {

    private NetworkRegistrationFabric() {}

    public static void initServer() {
        PayloadTypeRegistry.serverboundPlay().register(AllocateStatPacket.TYPE, AllocateStatPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LevelUpPacket.TYPE, LevelUpPacket.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AllocateStatPacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> AllocateStatPacket.handleOnServer(payload, context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(LevelUpPacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> LevelUpPacket.handleOnServer(payload, context.player())
                )
        );
    }
}
