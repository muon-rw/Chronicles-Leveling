package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-agnostic acrobatics-on-jump XP grant. Both loaders' jump signals feed
 * in here — Fabric via {@code PlayerJumpMixin} (no event ships), NeoForge via
 * {@code LivingJumpEvent}.
 */
public final class JumpXpHandler {

    private JumpXpHandler() {}

    public static void onJump(ServerPlayer player) {
        double xp = Configs.ACROBATICS.xpPerJump.get();
        if (xp <= 0) return;
        PlayerSkillManager.grantXp(player, Skills.ACROBATICS, (int) Math.round(xp));
    }
}
