package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.ability.AbilityCaster;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server. "Cast this ability." Carries an {@link Identifier}, never a slot index; selection
 * (single key / action-bar slot / radial) is entirely a client concern that resolves to an id, so the
 * activation UX can change with zero server edits. {@link AbilityCaster} re-derives the unlocked set and
 * re-checks every gate, so a forged id is harmless.
 */
public record CastAbilityPacket(Identifier abilityId) implements CustomPacketPayload {

    public static final Type<CastAbilityPacket> TYPE =
            new Type<>(ChroniclesLeveling.id("cast_ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastAbilityPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, CastAbilityPacket::abilityId,
                    CastAbilityPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(CastAbilityPacket packet, ServerPlayer player) {
        AbilityCaster.resolve(player, packet.abilityId());
    }
}
