package dev.muon.chronicles_leveling.network;

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
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.ability.AbilityCaster;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.Optional;

/**
 * Common-side packet entry points. Internal callers go through here so we have
 * one place to find every send-site and one place to add log/metrics later.
 *
 * <p>Player leveling state itself rides the loader's attachment sync (NeoForge
 * built-in, Fabric {@code syncWith}); this class only carries the input
 * packets where the client is asking the server to do something.
 */
public final class NetworkDispatcher {

    private NetworkDispatcher() {}

    private static NetworkHelper helper() {
        return Services.PLATFORM.getNetworkHelper();
    }

    public static void sendAllocateStat(String statId) {
        helper().sendToServer(new AllocateStatPacket(statId));
    }

    public static void sendLevelUp() {
        helper().sendToServer(LevelUpPacket.INSTANCE);
    }

    public static void sendResetStat(String statId, InteractionHand hand) {
        helper().sendToServer(new ResetStatPacket(statId, hand));
    }

    public static void sendUnlockSkillNode(String skillId, String perkId) {
        helper().sendToServer(new UnlockSkillNodePacket(skillId, perkId));
    }

    public static void sendRespecSkill(String skillId) {
        helper().sendToServer(new RespecSkillPacket(skillId));
    }

    public static void sendCastAbility(Identifier abilityId) {
        helper().sendToServer(new CastAbilityPacket(abilityId));
    }

    /** Begin a held cast (charge/channel). */
    public static void sendCastStart(Identifier abilityId) {
        helper().sendToServer(new CastStartPacket(abilityId));
    }

    /** End a held cast (release). */
    public static void sendCastRelease(Identifier abilityId) {
        helper().sendToServer(new CastReleasePacket(abilityId));
    }

    /** Tells the casting client why a cast (or in-progress channel) was denied. */
    public static void sendCastFailed(ServerPlayer player, Identifier abilityId, AbilityCaster.Denial denial) {
        helper().sendToPlayer(player, new CastFailedPacket(abilityId, denial.reason(),
                Optional.ofNullable(denial.message()), denial.detail()));
    }

    /** {@code empty} clears the slot. */
    public static void sendSetAbilitySlot(int slot, Optional<Identifier> abilityId) {
        helper().sendToServer(new SetAbilitySlotPacket(slot, abilityId));
    }

    public static void sendArcaneInsightClues(ServerPlayer player, int containerId, List<List<EnchantmentInstance>> slots) {
        helper().sendToPlayer(player, new ArcaneInsightCluesPacket(containerId, slots));
    }

    /** {@code table} empty clears the Wizard's Study glow. */
    public static void sendWizardsStudyTable(ServerPlayer player, Optional<GlobalPos> table) {
        helper().sendToPlayer(player, new WizardsStudyTablePacket(table));
    }

    /** Pushes the player's current active ability windows to their client (for client-predicted, window-gated effects). */
    public static void sendAbilityWindows(ServerPlayer player) {
        helper().sendToPlayer(player, new AbilityWindowsPacket(AbilityWindowStore.activeWindowsOf(player)));
    }
}
