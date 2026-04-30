package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import net.minecraft.world.entity.player.Player;

public final class PlayerSkillStoreNeoforge implements PlayerSkillStore {

    @Override
    public PlayerSkillData get(Player player) {
        return player.getData(NeoforgeAttachments.PLAYER_SKILLS);
    }

    @Override
    public void set(Player player, PlayerSkillData data) {
        player.setData(NeoforgeAttachments.PLAYER_SKILLS, data);
    }

    @Override
    public boolean has(Player player) {
        return player.getExistingDataOrNull(NeoforgeAttachments.PLAYER_SKILLS) != null;
    }
}
