package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.enchant.WizardsStudyClient;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

/**
 * Server -> client. The player's most-used enchanting table (empty when none, or the player lacks Wizard's
 * Study), so the client can glow that table's floating book. Sent on login, respawn, and each table enchant.
 */
public record WizardsStudyTablePacket(Optional<GlobalPos> table) implements CustomPacketPayload {

    public static final Type<WizardsStudyTablePacket> TYPE =
            new Type<>(ChroniclesLeveling.id("wizards_study_table"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WizardsStudyTablePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), WizardsStudyTablePacket::table,
                    WizardsStudyTablePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(WizardsStudyTablePacket packet) {
        WizardsStudyClient.accept(packet.table());
    }
}
