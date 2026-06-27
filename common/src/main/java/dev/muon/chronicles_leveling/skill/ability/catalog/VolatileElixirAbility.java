package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.alchemy.ElixirBrews;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Volatile Elixir (Alchemy, School of Negation): the offensive twin of {@link ExperimentalElixirAbility}:
 * distills a SPLASH potion of random HARMFUL effects (lingering once Negation Mastery is taken) from the same
 * held reagents, with the effect count shared from the root perk's rank. See {@link ElixirBrews}.
 */
public final class VolatileElixirAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/volatile_elixir");

    public VolatileElixirAbility() {
        super(ID, Skills.ALCHEMY, 600, AbilityCost.mana(20f));   // ~30s cooldown; placeholder tuning
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        return ElixirBrews.holdingReagent(player);
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.reagent");
    }

    @Override
    public void run(ServerPlayer player) {
        ElixirBrews.brew(player, MobEffectCategory.HARMFUL);
    }
}
