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
 * Mining (active capstone): "Vein Sense". Opens an ore-sight window; while it is active the client highlights
 * nearby ores through walls (VeinSightScanner finds them, VeinSightRenderer draws the boxes).
 */
public final class VeinSightAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/vein_sight");

    public VeinSightAbility() {
        super(ID, Skills.MINING, Configs.SKILLS.mining.veinSightCooldownTicks.get(), AbilityCost.none());
    }

    @Override
    public int durationTicks() {
        return Configs.SKILLS.mining.veinSightDurationTicks.get();
    }

    @Override
    public void run(ServerPlayer player) {
        AbilityWindowStore.open(player, AbilityWindowStore.WindowKind.VEIN_SIGHT, durationTicks());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.0f);
    }
}
