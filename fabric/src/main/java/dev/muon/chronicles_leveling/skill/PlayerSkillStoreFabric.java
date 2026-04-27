package dev.muon.chronicles_leveling.skill;

import net.minecraft.world.entity.player.Player;

public final class PlayerSkillStoreFabric implements PlayerSkillStore {

    @Override
    public PlayerSkillData get(Player player) {
        PlayerSkillData data = player.getAttached(PlayerSkillAttachmentFabric.SKILLS);
        return data != null ? data : PlayerSkillData.DEFAULT;
    }

    @Override
    public void set(Player player, PlayerSkillData data) {
        player.setAttached(PlayerSkillAttachmentFabric.SKILLS, data);
    }

    @Override
    public boolean has(Player player) {
        return player.hasAttached(PlayerSkillAttachmentFabric.SKILLS);
    }
}
