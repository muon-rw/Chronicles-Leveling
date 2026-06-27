package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillModifierApplier;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server. Player pressed the per-skill reset button in the tree screen (the open tree
 * implies which skill, so no hand/orb field). Refunds the whole tree at once.
 *
 * <p>Strictly simpler than {@code ResetStatPacket}: skill points are DERIVED ({@code respec()}
 * sets {@code spentPoints=0}), so the refund is implicit; there is no banked pool to credit back.
 * Recompute's removal authority is the full perk registry, so every now-cleared perk's modifier
 * lifts even though it left the unlocked set. The all-skills orb item is deferred post-v1.
 */
public record RespecSkillPacket(String skillId) implements CustomPacketPayload {

    public static final Type<RespecSkillPacket> TYPE =
            new Type<>(ChroniclesLeveling.id("respec_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RespecSkillPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RespecSkillPacket::skillId,
                    RespecSkillPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(RespecSkillPacket packet, ServerPlayer player) {
        if (SkillRegistry.get(packet.skillId()) == null) {
            return;   // unknown skill (registry is frozen well before any player can send this)
        }
        PlayerSkillData.SkillEntry entry = PlayerSkillManager.getSkill(player, packet.skillId());
        if (entry.spentPoints() <= 0) {
            return;
        }
        PlayerSkillManager.setSkill(player, packet.skillId(), entry.respec());
        SkillModifierApplier.recompute(player);
        PlayerSkillManager.reconcileAbilityBindings(player);   // drop slots/cooldowns for now-relocked abilities
        if (Skills.ENCHANTING.equals(packet.skillId())) {
            WizardsStudyHandler.syncTarget(player);   // refresh the table-glow cache if Wizard's Study was refunded
        }
    }
}
