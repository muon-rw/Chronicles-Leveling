package dev.muon.chronicles_leveling.network;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.network.message.AllocateStatPacket;
import dev.muon.chronicles_leveling.network.message.LevelUpPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge-side network channel registration.
 *
 * <p>Listens for {@code RegisterPayloadHandlersEvent} on the mod bus and
 * declares each payload + handler. Mirrors what
 * {@code NetworkRegistrationFabric} does on the other loader.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class NetworkRegistrationNeoforge {

    private NetworkRegistrationNeoforge() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ChroniclesLeveling.MOD_ID);
        registrar.playToServer(
                AllocateStatPacket.TYPE,
                AllocateStatPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> AllocateStatPacket.handleOnServer(
                        payload,
                        (net.minecraft.server.level.ServerPlayer) context.player()))
        );
        registrar.playToServer(
                LevelUpPacket.TYPE,
                LevelUpPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> LevelUpPacket.handleOnServer(
                        payload,
                        (net.minecraft.server.level.ServerPlayer) context.player()))
        );
    }
}
