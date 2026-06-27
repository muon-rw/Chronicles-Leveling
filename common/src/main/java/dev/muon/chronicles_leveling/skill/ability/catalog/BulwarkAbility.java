package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Defense: arms a short parry window that fully negates the next incoming hit (consumed once by the
 * {@code isInvulnerableTo} mixin). Proves a window the damage path consumes, plus {@link #canActivate}
 * as a gate distinct from unlock/cooldown/cost (you can't parry mid item-use).
 */
public final class BulwarkAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/bulwark");

    public BulwarkAbility() {
        super(ID, Skills.DEFENSE, 160, AbilityCost.stamina(10f));   // ~8s cooldown; placeholder tuning
    }

    @Override
    public int durationTicks() {
        return 10;   // ~0.5s parry window
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        return !player.isUsingItem();
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.using_item");
    }

    @Override
    public void run(ServerPlayer player) {
        AbilityWindowStore.open(player, AbilityWindowStore.WindowKind.PARRY_ARMED, durationTicks());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1f);
    }
}
