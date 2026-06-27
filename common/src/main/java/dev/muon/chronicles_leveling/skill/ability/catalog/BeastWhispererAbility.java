package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.social.SpeechTamingHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Speech: raycast for the tameable you are looking at and instantly tame it. */
public final class BeastWhispererAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/beast_whisperer");

    public BeastWhispererAbility() {
        super(ID, Skills.SPEECH,
                Configs.SKILLS.speech.beastWhispererCooldownTicks.get(),
                AbilityCost.stamina(Configs.SKILLS.speech.beastWhispererStaminaCost.get().floatValue()));
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        return SpeechTamingHandler.findTameable(player, Configs.SKILLS.speech.beastWhispererRange.get()) != null;
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.no_tameable");
    }

    @Override
    public void run(ServerPlayer player) {
        if (SpeechTamingHandler.instantTame(player, Configs.SKILLS.speech.beastWhispererRange.get())) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 1f, 1.3f);
        }
    }
}
