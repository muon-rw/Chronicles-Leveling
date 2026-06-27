package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.AbilityWindowStoreClient;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server -> client. The owning player's currently-active ability windows (kind + absolute end-tick), so client
 * code can predict window-gated effects (e.g. Superbreaker's instant-break) and emit their feedback locally.
 * Resent whenever a window opens or is consumed; expiry needs no packet (the client compares against the end-tick).
 */
public record AbilityWindowsPacket(List<AbilityWindowStore.ActiveWindow> windows) implements CustomPacketPayload {

    public static final Type<AbilityWindowsPacket> TYPE = new Type<>(ChroniclesLeveling.id("ability_windows"));

    private static final StreamCodec<RegistryFriendlyByteBuf, AbilityWindowStore.ActiveWindow> WINDOW_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, (AbilityWindowStore.ActiveWindow w) -> w.kind().ordinal(),
                    ByteBufCodecs.VAR_LONG, AbilityWindowStore.ActiveWindow::endTick,
                    (ordinal, endTick) -> new AbilityWindowStore.ActiveWindow(AbilityWindowStore.WindowKind.values()[ordinal], endTick));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityWindowsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    WINDOW_CODEC.apply(ByteBufCodecs.list()), AbilityWindowsPacket::windows,
                    AbilityWindowsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(AbilityWindowsPacket packet) {
        AbilityWindowStoreClient.accept(packet.windows());
    }
}
