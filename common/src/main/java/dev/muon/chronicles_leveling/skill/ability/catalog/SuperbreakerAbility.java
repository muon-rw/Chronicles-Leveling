package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Mining (active, leveled): opens a window during which pickaxe-mineable blocks break instantly (enforced by
 * {@code PlayerMixin}). Duration scales with the perk's rank.
 */
public final class SuperbreakerAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/superbreaker");

    private static final String PERK_ID = "superbreaker";

    public SuperbreakerAbility() {
        super(ID, Skills.MINING, Configs.SKILLS.mining.superbreakerCooldownTicks.get(), AbilityCost.none());
    }

    @Override
    public int durationTicks() {
        return Configs.SKILLS.mining.superbreakerDurationPerRankTicks.get();   // rank-1 representative; the tooltip scales by rank
    }

    @Override
    public void run(ServerPlayer player) {
        int rank = Math.max(1, PlayerSkillManager.get(player).get(Skills.MINING).rankOf(PERK_ID));
        int ticks = Configs.SKILLS.mining.superbreakerDurationPerRankTicks.get() * rank;
        AbilityWindowStore.open(player, AbilityWindowStore.WindowKind.SUPERBREAKER, ticks);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f);
    }
}
