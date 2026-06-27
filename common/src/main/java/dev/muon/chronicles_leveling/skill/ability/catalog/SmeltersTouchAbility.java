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

/** Mining (active): opens a window during which mined ores drop their smelted result (read by GatherProcRouter). */
public final class SmeltersTouchAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/smelters_touch");

    public SmeltersTouchAbility() {
        super(ID, Skills.MINING, Configs.SKILLS.mining.smeltersTouchCooldownTicks.get(), AbilityCost.none());
    }

    @Override
    public int durationTicks() {
        return Configs.SKILLS.mining.smeltersTouchDurationTicks.get();
    }

    @Override
    public void run(ServerPlayer player) {
        AbilityWindowStore.open(player, AbilityWindowStore.WindowKind.SMELTERS_TOUCH, durationTicks());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
    }
}
