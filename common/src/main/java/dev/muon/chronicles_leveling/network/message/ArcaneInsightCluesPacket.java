package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.enchant.ArcaneInsightClues;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;

/**
 * Server → client. The full would-be enchantment list for each of the 3 vanilla enchant-table slots, so Arcane
 * Insight can reveal more than vanilla's single clue. The client cannot reproduce the roll (the enchant seed is
 * truncated to 16 bits over the data-slot sync), so the server sends the exact lists it rolled. Only sent to a
 * perk-holder; {@link ArcaneInsightClues} caches it by container id.
 */
public record ArcaneInsightCluesPacket(int containerId, List<List<EnchantmentInstance>> slots)
        implements CustomPacketPayload {

    public static final Type<ArcaneInsightCluesPacket> TYPE =
            new Type<>(ChroniclesLeveling.id("arcane_insight_clues"));

    private static final StreamCodec<RegistryFriendlyByteBuf, EnchantmentInstance> INSTANCE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT), EnchantmentInstance::enchantment,
                    ByteBufCodecs.VAR_INT, EnchantmentInstance::level,
                    EnchantmentInstance::new
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, List<EnchantmentInstance>> SLOT_CODEC =
            INSTANCE_CODEC.apply(ByteBufCodecs.list());

    private static final StreamCodec<RegistryFriendlyByteBuf, List<List<EnchantmentInstance>>> SLOTS_CODEC =
            SLOT_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneInsightCluesPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.CONTAINER_ID, ArcaneInsightCluesPacket::containerId,
                    SLOTS_CODEC, ArcaneInsightCluesPacket::slots,
                    ArcaneInsightCluesPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(ArcaneInsightCluesPacket packet) {
        ArcaneInsightClues.accept(packet.containerId(), packet.slots());
    }
}
