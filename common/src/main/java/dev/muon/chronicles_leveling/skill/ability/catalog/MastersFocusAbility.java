package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Weaponry capstone: opens a {@link AbilityWindowStore.WindowKind#MASTERS_FOCUS} window during which every
 * melee hit crits, and a hit that would already have critted deals bonus true damage (the combat-proc
 * layer reads the window). The window is the whole effect; this just opens it.
 */
public final class MastersFocusAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/masters_focus");

    public MastersFocusAbility() {
        super(ID, Skills.WEAPONRY, 600, AbilityCost.stamina(20f));   // ~30s cooldown; placeholder tuning
    }

    @Override
    public int durationTicks() {
        return Configs.SKILLS.weaponry.mastersFocusDurationTicks.get();
    }

    @Override
    public void run(ServerPlayer player) {
        AbilityWindowStore.open(player, AbilityWindowStore.WindowKind.MASTERS_FOCUS, durationTicks());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1f);
    }
}
