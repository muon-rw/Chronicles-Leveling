package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server. Player clicked the {@code +} button next to the Level row
 * in the level-up screen. Server validates that the player has banked enough
 * XP for the next rung and, if so, debits XP, raises the level, and credits
 * the configured points-per-level into the unspent pool.
 *
 * <p>No payload — the request is "level me up if you can".
 */
public record LevelUpPacket() implements CustomPacketPayload {

    public static final LevelUpPacket INSTANCE = new LevelUpPacket();

    public static final Type<LevelUpPacket> TYPE =
            new Type<>(ChroniclesLeveling.id("level_up"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LevelUpPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(LevelUpPacket packet, ServerPlayer player) {
        PlayerLevelManager.tryLevelUp(player);
    }
}
