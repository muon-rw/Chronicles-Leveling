package dev.muon.chronicles_leveling.network;

import dev.muon.chronicles_leveling.network.message.AbilityWindowsPacket;
import dev.muon.chronicles_leveling.network.message.AllocateStatPacket;
import dev.muon.chronicles_leveling.network.message.ArcaneInsightCluesPacket;
import dev.muon.chronicles_leveling.network.message.CastAbilityPacket;
import dev.muon.chronicles_leveling.network.message.CastFailedPacket;
import dev.muon.chronicles_leveling.network.message.CastReleasePacket;
import dev.muon.chronicles_leveling.network.message.CastStartPacket;
import dev.muon.chronicles_leveling.network.message.LevelUpPacket;
import dev.muon.chronicles_leveling.network.message.RespecSkillPacket;
import dev.muon.chronicles_leveling.network.message.ResetStatPacket;
import dev.muon.chronicles_leveling.network.message.SetAbilitySlotPacket;
import dev.muon.chronicles_leveling.network.message.UnlockSkillNodePacket;
import dev.muon.chronicles_leveling.network.message.WizardsStudyTablePacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class NetworkRegistrationFabric {

    private NetworkRegistrationFabric() {}

    public static void initServer() {
        PayloadTypeRegistry.serverboundPlay().register(AllocateStatPacket.TYPE, AllocateStatPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LevelUpPacket.TYPE, LevelUpPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ResetStatPacket.TYPE, ResetStatPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UnlockSkillNodePacket.TYPE, UnlockSkillNodePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RespecSkillPacket.TYPE, RespecSkillPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CastAbilityPacket.TYPE, CastAbilityPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CastStartPacket.TYPE, CastStartPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CastReleasePacket.TYPE, CastReleasePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetAbilitySlotPacket.TYPE, SetAbilitySlotPacket.STREAM_CODEC);

        // S2C: types are registered on both sides (server encodes, client decodes); the client receivers
        // live in the client entrypoint.
        PayloadTypeRegistry.clientboundPlay().register(ArcaneInsightCluesPacket.TYPE, ArcaneInsightCluesPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WizardsStudyTablePacket.TYPE, WizardsStudyTablePacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AbilityWindowsPacket.TYPE, AbilityWindowsPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CastFailedPacket.TYPE, CastFailedPacket.STREAM_CODEC);

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
        ServerPlayNetworking.registerGlobalReceiver(ResetStatPacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> ResetStatPacket.handleOnServer(payload, context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(UnlockSkillNodePacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> UnlockSkillNodePacket.handleOnServer(payload, context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(RespecSkillPacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> RespecSkillPacket.handleOnServer(payload, context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(CastAbilityPacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> CastAbilityPacket.handleOnServer(payload, context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(CastStartPacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> CastStartPacket.handleOnServer(payload, context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(CastReleasePacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> CastReleasePacket.handleOnServer(payload, context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(SetAbilitySlotPacket.TYPE,
                (payload, context) -> context.server().execute(
                        () -> SetAbilitySlotPacket.handleOnServer(payload, context.player())
                )
        );
    }
}
