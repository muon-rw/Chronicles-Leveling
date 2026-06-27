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
 * Experimental Elixir (Alchemy root): distills a drinkable elixir of random BENEFICIAL effects from a held
 * reagent (glass bottle, water bottle, or awkward potion); the root perk's rank sets the effect count (1..3).
 * Random amplifiers and durations keep the gamble; mana plus a long cooldown is the cost. See
 * {@link ElixirBrews} for the shared distillation rules.
 */
public final class ExperimentalElixirAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/experimental_elixir");

    public ExperimentalElixirAbility() {
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
        ElixirBrews.brew(player, MobEffectCategory.BENEFICIAL);
    }
}
