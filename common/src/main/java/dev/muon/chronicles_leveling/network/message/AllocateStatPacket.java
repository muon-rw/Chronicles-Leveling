package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.StatModifierApplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

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
 * <p>If both pass: debit one point, raise the stat attribute base by 1, and
 * recompute secondary modifiers. The sync to the owner happens for free
 * because the unspent-points debit goes through {@code PlayerLevelManager.set}.
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
        if (!ModStats.isRegistered(packet.statId())) {
            ChroniclesLeveling.LOG.debug("Player {} requested unknown stat '{}', ignoring",
                    player.getName().getString(), packet.statId());
            return;
        }
        if (!PlayerLevelManager.trySpendPoint(player)) {
            return;
        }

        AttributeInstance instance = player.getAttribute(ModStats.get(packet.statId()));
        if (instance == null) return;
        instance.setBaseValue(instance.getBaseValue() + 1.0);

        StatModifierApplier.recompute(player);
    }
}
