package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.StatModifierApplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server. Player clicked the {@code +} button next to a stat in the
 * level-up screen.
 *
 * <p>Server validates that:
 * <ol>
 *   <li>{@link #statId} matches a registered stat (rejecting client-side
 *       custom stat ids before we ever touch the unspent-points pool).</li>
 *   <li>The player has at least one unspent point.</li>
 * </ol>
 *
 * <p>If both pass: debit one point, increment the player's allocation in the
 * attachment, and recompute the per-stat allocation modifier + downstream
 * secondaries. The sync to the owner happens for free because the attachment
 * write goes through {@code PlayerLevelManager.set}.
 */
public record AllocateStatPacket(String statId) implements CustomPacketPayload {

    public static final Type<AllocateStatPacket> TYPE =
            new Type<>(ChroniclesLeveling.id("allocate_stat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AllocateStatPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AllocateStatPacket::statId,
                    AllocateStatPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Common server-side handler. Loader code routes incoming packets here. */
    public static void handleOnServer(AllocateStatPacket packet, ServerPlayer player) {
        String statId = packet.statId();
        if (!ModStats.isRegistered(statId)) {
            ChroniclesLeveling.LOG.debug("Player {} requested unknown stat '{}', ignoring",
                    player.getName().getString(), statId);
            return;
        }

        PlayerLevelData data = PlayerLevelManager.get(player);
        int current = data.allocation(statId);

        // maxStatLevel caps only the player's own allocation; external sources stack via
        // attribute modifiers and are intentionally uncapped.
        int maxStatLevel = Configs.SYNC.maxStatLevel.get();
        if (maxStatLevel > 0 && current >= maxStatLevel) return;

        if (data.unspentPoints() <= 0) return;

        PlayerLevelManager.set(player, data
                .withUnspentPoints(data.unspentPoints() - 1)
                .withAllocation(statId, current + 1));

        StatModifierApplier.recompute(player);
    }
}
