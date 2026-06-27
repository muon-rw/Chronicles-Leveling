package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.ability.runtime.HeldCastDriver;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server. End a held cast (charge fires scaled by hold, channel stops). Sent on key-up; also sent
 * defensively when the held key is no longer down. Harmless if no held cast is active for the player.
 */
public record CastReleasePacket(Identifier abilityId) implements CustomPacketPayload {

    public static final Type<CastReleasePacket> TYPE = new Type<>(ChroniclesLeveling.id("cast_release"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastReleasePacket> STREAM_CODEC =
            StreamCodec.composite(Identifier.STREAM_CODEC, CastReleasePacket::abilityId, CastReleasePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(CastReleasePacket packet, ServerPlayer player) {
        HeldCastDriver.release(player, packet.abilityId());
    }
}
