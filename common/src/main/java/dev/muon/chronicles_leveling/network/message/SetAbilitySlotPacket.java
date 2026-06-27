package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.ability.AbilitySlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Client → server. Binds an ability to (or clears) an action-bar slot. Server-authoritative so the
 * binding is relog-safe: an empty {@code abilityId} clears the slot; otherwise the id must resolve to a
 * registered ability the player has actually unlocked, else the request is dropped.
 */
public record SetAbilitySlotPacket(int slot, Optional<Identifier> abilityId) implements CustomPacketPayload {

    public static final Type<SetAbilitySlotPacket> TYPE =
            new Type<>(ChroniclesLeveling.id("set_ability_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetAbilitySlotPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetAbilitySlotPacket::slot,
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC), SetAbilitySlotPacket::abilityId,
                    SetAbilitySlotPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetAbilitySlotPacket packet, ServerPlayer player) {
        if (!AbilitySlots.isValid(packet.slot())) {
            return;
        }
        if (packet.abilityId().isEmpty()) {
            PlayerSkillManager.setAbilitySlot(player, packet.slot(), null);
            return;
        }
        Identifier id = packet.abilityId().get();
        if (SkillRegistry.ability(id) == null || !SkillEffects.hasAbility(player, id)) {
            return;   // unknown ability, or one this player hasn't unlocked
        }
        PlayerSkillManager.setAbilitySlot(player, packet.slot(), id.toString());
    }
}
