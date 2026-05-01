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
 * <p>XP gain hooks (combat, brewing, etc.) call {@link #grantXp} which handles
 * level-up rollover via {@link SkillCurve}.
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

    /**
     * Bank XP into the given skill, rolling level-ups as the curve dictates.
     * No-ops on zero/negative grants and unknown skill ids — keeps every gain
     * hook from having to check those itself.
     *
     * <p>Single write to the store at the end (one persist + one sync) even if
     * the grant covers multiple levels.
     */
    public static void grantXp(ServerPlayer player, String skillId, int amount) {
        if (amount <= 0 || !Skills.isRegistered(skillId)) return;
        PlayerSkillData.SkillEntry entry = getSkill(player, skillId);
        int level = entry.level();
        long xp = (long) entry.xp() + amount;
        int xpForNext = SkillCurve.xpToNext(skillId, level);
        // Bound rollover iterations: a degenerate curve that always returns 1 paired
        // with a huge grant would otherwise spin until int overflow on the server thread.
        int rolled = 0;
        while (xp >= xpForNext && rolled++ < MAX_LEVELS_PER_GRANT) {
            xp -= xpForNext;
            level++;
            xpForNext = SkillCurve.xpToNext(skillId, level);
        }
        setSkill(player, skillId, new PlayerSkillData.SkillEntry(level, (int) xp));
    }

    /** Round-and-grant convenience for handlers that compute XP as a {@code double}. */
    public static void grantXp(ServerPlayer player, String skillId, double amount) {
        if (amount <= 0) return;
        grantXp(player, skillId, (int) Math.round(amount));
    }

    private static final int MAX_LEVELS_PER_GRANT = 1000;
}
