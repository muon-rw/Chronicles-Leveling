package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side facade for reading + writing player skill state.
 *
 * <p>All routes that mutate skill levels/xp go through this class so we have
 * exactly one place to dispatch persistence + sync via the loader-specific
 * {@link PlayerSkillStore}. Read-side accessors are safe on the client too —
 * the attachment is synced to the owning client by the platform layer.
 *
 * <p>XP-gain hooks aren't implemented yet; this class exposes the primitives
 * (set/setSkill) that those hooks will call once they land.
 */
public final class PlayerSkillManager {

    private PlayerSkillManager() {}

    public static PlayerSkillData get(Player player) {
        return Services.PLATFORM.getPlayerSkillStore().get(player);
    }

    public static PlayerSkillData.SkillEntry getSkill(Player player, String skillId) {
        return get(player).get(skillId);
    }

    public static void set(ServerPlayer player, PlayerSkillData data) {
        Services.PLATFORM.getPlayerSkillStore().set(player, data);
    }

    public static void setSkill(ServerPlayer player, String skillId, PlayerSkillData.SkillEntry entry) {
        set(player, get(player).with(skillId, entry));
    }
}
