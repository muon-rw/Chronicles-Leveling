package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.social.SpeechTamingHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Speech: quiet nearby hostiles so they drop their target and cannot attack for the duration (radius/duration scale with rank). */
public final class PacifyAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/pacify");

    public PacifyAbility() {
        super(ID, Skills.SPEECH,
                Configs.SKILLS.speech.pacifyCooldownTicks.get(),
                AbilityCost.stamina(Configs.SKILLS.speech.pacifyStaminaCost.get().floatValue()));
    }

    @Override
    public int durationTicks() {
        return Configs.SKILLS.speech.pacifyDurationTicksBase.get();
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        return !SpeechTamingHandler.pacifiableMobs(player, radius(player)).isEmpty();
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.no_hostiles");
    }

    @Override
    public void run(ServerPlayer player) {
        int rank = Math.max(1, PlayerSkillManager.get(player).get(Skills.SPEECH).rankOf("pacify"));
        var sp = Configs.SKILLS.speech;
        int duration = sp.pacifyDurationTicksBase.get() + sp.pacifyDurationTicksPerRank.get() * (rank - 1);
        SpeechTamingHandler.pacifyArea(player, radius(player), duration);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f);
    }

    private static double radius(ServerPlayer player) {
        int rank = Math.max(1, PlayerSkillManager.get(player).get(Skills.SPEECH).rankOf("pacify"));
        var sp = Configs.SKILLS.speech;
        return sp.pacifyRadiusBase.get() + sp.pacifyRadiusPerRank.get() * (rank - 1);
    }
}
