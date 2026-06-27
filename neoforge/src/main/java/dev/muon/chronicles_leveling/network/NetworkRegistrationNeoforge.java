package dev.muon.chronicles_leveling.network;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
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
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                LevelUpPacket.TYPE,
                LevelUpPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> LevelUpPacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                ResetStatPacket.TYPE,
                ResetStatPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ResetStatPacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                UnlockSkillNodePacket.TYPE,
                UnlockSkillNodePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> UnlockSkillNodePacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                RespecSkillPacket.TYPE,
                RespecSkillPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> RespecSkillPacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                CastAbilityPacket.TYPE,
                CastAbilityPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CastAbilityPacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                SetAbilitySlotPacket.TYPE,
                SetAbilitySlotPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SetAbilitySlotPacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                CastStartPacket.TYPE,
                CastStartPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CastStartPacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToServer(
                CastReleasePacket.TYPE,
                CastReleasePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CastReleasePacket.handleOnServer(
                        payload,
                        (ServerPlayer) context.player()))
        );
        registrar.playToClient(
                ArcaneInsightCluesPacket.TYPE,
                ArcaneInsightCluesPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ArcaneInsightCluesPacket.handleOnClient(payload))
        );
        registrar.playToClient(
                WizardsStudyTablePacket.TYPE,
                WizardsStudyTablePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> WizardsStudyTablePacket.handleOnClient(payload))
        );
        registrar.playToClient(
                AbilityWindowsPacket.TYPE,
                AbilityWindowsPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> AbilityWindowsPacket.handleOnClient(payload))
        );
        registrar.playToClient(
                CastFailedPacket.TYPE,
                CastFailedPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CastFailedPacket.handleOnClient(payload))
        );
    }
}
