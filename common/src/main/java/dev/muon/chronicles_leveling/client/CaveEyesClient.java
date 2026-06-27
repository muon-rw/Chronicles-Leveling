package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client read for the Cave Eyes perk (Mining): the faint, always-on night-vision intensity to apply. Reads the
 * perk rank straight off the synced {@code PlayerSkillData} (cheap, no per-frame capability derive); the lightmap
 * mixin queries this each extract. Deliberately unconditional: the effect is weak enough not to intrude on the
 * surface, so it needs no sky-exposure gate.
 */
public final class CaveEyesClient {

    private CaveEyesClient() {}

    public static float intensity(LocalPlayer player) {
        if (PlayerSkillManager.get(player).get(Skills.MINING).rankOf("cave_eyes") < 1) {
            return 0f;
        }
        return Configs.SKILLS.mining.caveEyesNightVisionScale.get().floatValue();
    }
}
