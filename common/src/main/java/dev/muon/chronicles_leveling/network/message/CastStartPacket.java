package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.ability.runtime.HeldCastDriver;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server. Begin a held cast (charge or channel) for an ability. The client sends this on key-down for a
 * non-INSTANT ability; {@link CastReleasePacket} follows on key-up. The server gates and tracks it; a forged id is harmless.
 */
public record CastStartPacket(Identifier abilityId) implements CustomPacketPayload {

    public static final Type<CastStartPacket> TYPE = new Type<>(ChroniclesLeveling.id("cast_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastStartPacket> STREAM_CODEC =
            StreamCodec.composite(Identifier.STREAM_CODEC, CastStartPacket::abilityId, CastStartPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(CastStartPacket packet, ServerPlayer player) {
        HeldCastDriver.start(player, packet.abilityId());
    }
}
