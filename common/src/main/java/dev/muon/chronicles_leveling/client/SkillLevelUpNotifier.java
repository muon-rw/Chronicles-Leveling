package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Plays {@code chronicles_leveling:level_up} and shows an above-hotbar message whenever a synced skill level
 * increases. Polled from the client tick (mirrors {@link XpAffordabilityNotifier}); diffing the synced
 * {@link PlayerSkillData} levels catches every XP path without a dedicated packet.
 */
public final class SkillLevelUpNotifier {

    /** null until the first observed tick, so a world-join / re-login reseeds without firing. */
    private static Map<String, Integer> lastLevels = null;

    private SkillLevelUpNotifier() {}

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            lastLevels = null;
            return;
        }
        boolean seeded = lastLevels != null;
        Map<String, Integer> current = new HashMap<>();
        for (Map.Entry<String, PlayerSkillData.SkillEntry> entry : PlayerSkillManager.get(player).skills().entrySet()) {
            String skillId = entry.getKey();
            int level = entry.getValue().level();
            current.put(skillId, level);
            if (seeded) {
                int previous = lastLevels.getOrDefault(skillId, 1);   // unseen skill starts at level 1
                if (level > previous) {
                    notifyLevelUp(skillId, previous, level);
                }
            }
        }
        lastLevels = current;
    }

    private static void notifyLevelUp(String skillId, int oldLevel, int newLevel) {
        SkillDefinition def = SkillRegistry.get(skillId);
        Component name = def != null ? def.display() : Component.literal(skillId);
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setOverlayMessage(Component.translatable("chronicles_leveling.skill.level_up", name, oldLevel, newLevel), false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LEVEL_UP.value(), 1.0f));
    }
}
