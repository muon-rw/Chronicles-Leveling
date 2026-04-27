package dev.muon.chronicles_leveling.skill;

import net.minecraft.world.entity.player.Player;

public final class PlayerSkillStoreNeoforge implements PlayerSkillStore {

    @Override
    public PlayerSkillData get(Player player) {
        return player.getData(PlayerSkillAttachmentNeoforge.SKILLS);
    }

    @Override
    public void set(Player player, PlayerSkillData data) {
        player.setData(PlayerSkillAttachmentNeoforge.SKILLS, data);
    }

    @Override
    public boolean has(Player player) {
        return player.getExistingDataOrNull(PlayerSkillAttachmentNeoforge.SKILLS) != null;
    }
}
