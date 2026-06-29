package dev.muon.chronicles_leveling.network.message;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillModifierApplier;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilitySlots;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import dev.muon.chronicles_leveling.skill.perk.AbilityUnlock;
import dev.muon.chronicles_leveling.skill.perk.PerkEffect;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server. Player clicked an AVAILABLE node in the skill tree to buy one rank.
 *
 * <p>A distinct single-purpose packet (not a parameterized {@code AllocateStatPacket}): the stat
 * spend debits a banked {@code unspentPoints} pool against a flat cap, whereas a skill spend
 * validates {@code (skillId, perkId)} against the <em>frozen registry</em> (prerequisites and
 * {@code maxRank}) and DERIVED available points. Two single-responsibility records beat a
 * discriminator + branchy handler, matching the stat side's own three-packet split.
 *
 * <p>Server-authoritative: trust only the two strings, debug-log + return on any miss, and never
 * read a target rank off the wire; one accepted packet advances at most one rank.
 */
public record UnlockSkillNodePacket(String skillId, String perkId) implements CustomPacketPayload {

    public static final Type<UnlockSkillNodePacket> TYPE =
            new Type<>(ChroniclesLeveling.id("unlock_skill_node"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockSkillNodePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, UnlockSkillNodePacket::skillId,
                    ByteBufCodecs.STRING_UTF8, UnlockSkillNodePacket::perkId,
                    UnlockSkillNodePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(UnlockSkillNodePacket packet, ServerPlayer player) {
        // Synchronous read-modify-write on the server thread (Fabric server.execute / NeoForge
        // enqueueWork). getSkill → withPerkRank → setSkill → recompute never yields, so two same-tick
        // spend packets serialize and the second observes the first's write; concurrent affordability
        // checks against shared availablePoints cannot double-spend. Keep it yield-free.
        SkillDefinition def = SkillRegistry.get(packet.skillId());
        if (def == null) {
            return;   // unknown skill (registry is frozen well before any player can send this)
        }
        SkillPerk perk = def.perk(packet.perkId());
        if (perk == null) {
            return;   // invented perk id
        }

        PlayerSkillData.SkillEntry entry = PlayerSkillManager.getSkill(player, packet.skillId());
        int current = entry.rankOf(packet.perkId());
        if (current >= perk.maxRank()) {
            return;   // already maxed (also covers single-rank nodes)
        }
        if (!perk.prerequisitesMet(pre -> entry.rankOf(pre) >= 1)) {
            return;   // not enough prerequisites unlocked (handles "any K of N")
        }
        if (entry.availablePoints(def.totalCost()) < perk.costOfNextRank(current)) {
            return;
        }

        PlayerSkillManager.setSkill(player, packet.skillId(),
                entry.withPerkRank(packet.perkId(), current + 1,
                        (id, r) -> { SkillPerk priced = def.perk(id); return priced == null ? 0 : priced.costThroughRank(r); }));
        SkillModifierApplier.recompute(player);
        if (Skills.ENCHANTING.equals(packet.skillId())) {
            WizardsStudyHandler.syncTarget(player);   // surface the table glow as soon as Wizard's Study is unlocked
        }
        if (current == 0) {
            autoAssignNewAbility(player, perk);   // first unlock of an active → drop it in the next empty slot
        }
    }

    /** On first unlock of an ability perk, binds it to the lowest empty action-bar slot (no-op if full or already bound). */
    private static void autoAssignNewAbility(ServerPlayer player, SkillPerk perk) {
        Identifier ability = null;
        for (PerkEffect effect : perk.effectsAtRank(1)) {
            if (effect instanceof AbilityUnlock unlock) {
                ability = unlock.abilityId();
                break;
            }
        }
        if (ability == null) {
            return;
        }
        PlayerSkillData data = PlayerSkillManager.get(player);
        if (data.slotOf(ability.toString()) >= 0) {
            return;
        }
        for (int slot = 0; slot < AbilitySlots.COUNT; slot++) {
            if (data.slotAbility(slot) == null) {
                PlayerSkillManager.setAbilitySlot(player, slot, ability.toString());
                return;
            }
        }
    }
}
