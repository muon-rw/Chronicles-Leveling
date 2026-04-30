package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.attachment.FabricAttachments;
import net.minecraft.world.entity.player.Player;

public final class PlayerSkillStoreFabric implements PlayerSkillStore {

    @Override
    public PlayerSkillData get(Player player) {
        PlayerSkillData data = player.getAttached(FabricAttachments.PLAYER_SKILLS);
        return data != null ? data : PlayerSkillData.DEFAULT;
    }

    @Override
    public void set(Player player, PlayerSkillData data) {
        player.setAttached(FabricAttachments.PLAYER_SKILLS, data);
    }

    @Override
    public boolean has(Player player) {
        return player.hasAttached(FabricAttachments.PLAYER_SKILLS);
    }
}
