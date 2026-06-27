package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.AbilityFeedbackClient;
import dev.muon.chronicles_leveling.skill.ability.CastDenyReason;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Server -> client. A cast (or an in-progress channel) was denied. The client builds the message from {@code reason}
 * (and {@code detail}, e.g. cooldown seconds), unless {@code message} carries an ability-supplied component
 * (UNAVAILABLE), and plays a local reaction. Replaces a raw action-bar text packet so the client can react, not just read.
 */
public record CastFailedPacket(Identifier abilityId, CastDenyReason reason, Optional<Component> message, int detail)
        implements CustomPacketPayload {

    public static final Type<CastFailedPacket> TYPE = new Type<>(ChroniclesLeveling.id("cast_failed"));

    private static final CastDenyReason[] REASONS = CastDenyReason.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, CastFailedPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, CastFailedPacket::abilityId,
                    ByteBufCodecs.VAR_INT.map(i -> i >= 0 && i < REASONS.length ? REASONS[i] : CastDenyReason.UNAVAILABLE,
                            CastDenyReason::ordinal), CastFailedPacket::reason,
                    ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs::optional), CastFailedPacket::message,
                    ByteBufCodecs.VAR_INT, CastFailedPacket::detail,
                    CastFailedPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(CastFailedPacket packet) {
        AbilityFeedbackClient.onCastFailed(packet.abilityId(), packet.reason(), packet.message().orElse(null), packet.detail());
    }
}
