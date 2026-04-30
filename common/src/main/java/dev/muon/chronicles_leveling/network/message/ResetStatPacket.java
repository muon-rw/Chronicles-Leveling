package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.item.ModItems;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.sounds.ModSounds;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.StatModifierApplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server. Player pressed ENTER on a selected stat in the orb-of-regret
 * reset flow.
 *
 * <p>Server validates that:
 * <ol>
 *   <li>The declared {@link #hand} still holds an Orb of Regret. (The flow is
 *       modal but a player could legitimately swap mid-flow if Screen input
 *       leaks; servers should never trust that.)</li>
 *   <li>{@link #statId} matches a registered stat.</li>
 * </ol>
 *
 * <p>If both pass: refund the stat's current allocation back into unspent
 * points, clear the allocation, recompute modifiers, and consume one orb.
 */
public record ResetStatPacket(String statId, InteractionHand hand) implements CustomPacketPayload {

    public static final Type<ResetStatPacket> TYPE =
            new Type<>(ChroniclesLeveling.id("reset_stat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResetStatPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ResetStatPacket::statId,
                    InteractionHand.STREAM_CODEC, ResetStatPacket::hand,
                    ResetStatPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ResetStatPacket packet, ServerPlayer player) {
        String statId = packet.statId();
        if (!ModStats.isRegistered(statId)) {
            ChroniclesLeveling.LOG.info("Player {} requested reset of unknown stat '{}', ignoring",
                    player.getName().getString(), statId);
            return;
        }

        ItemStack held = player.getItemInHand(packet.hand());
        if (ModItems.LESSER_ORB_OF_REGRET == null
                || held.isEmpty()
                || !held.is(ModItems.LESSER_ORB_OF_REGRET.value())) {
            ChroniclesLeveling.LOG.warn("Player {} reset request rejected: not holding an orb in {}",
                    player.getName().getString(), packet.hand());
            return;
        }

        PlayerLevelData data = PlayerLevelManager.get(player);
        int refund = data.allocation(statId);
        if (refund <= 0) {
            // Nothing to refund — don't consume the orb, just drop the request.
            return;
        }

        PlayerLevelManager.set(player, data
                .withAllocation(statId, 0)
                .withUnspentPoints(data.unspentPoints() + refund));
        StatModifierApplier.recompute(player);

        held.shrink(1);

        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.LEVEL_UP.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
