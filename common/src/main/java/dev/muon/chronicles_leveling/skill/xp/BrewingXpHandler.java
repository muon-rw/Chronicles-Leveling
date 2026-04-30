package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.config.skill.AlchemyRecipeXp;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Map;

/**
 * Static helpers for the alchemy XP grant. The brewing-stand BE mixin is
 * authoritative for "this slot was actually brewed here" via per-slot flags
 * persisted on the BE; this class only computes the XP value for a given
 * brewed potion and writes it to the player.
 *
 * <p>Recipe identity is keyed by the output {@link Potion}'s id (e.g.
 * {@code minecraft:strength}) rather than the brewing-recipe id, because
 * vanilla brewing recipes don't have stable ids — they live in
 * {@code PotionBrewing.Mix} records, not a registry. Output potion ids are
 * stable, registered, and what packs visibly configure.
 */
public final class BrewingXpHandler {

    private BrewingXpHandler() {}

    public static void grantForBrewedPotion(ServerPlayer player, ItemStack stack) {
        double xp = xpFor(stack);
        if (xp <= 0) return;
        PlayerSkillManager.grantXp(player, Skills.ALCHEMY, (int) Math.round(xp));
    }

    private static double xpFor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return 0;

        Identifier potionId = contents.potion()
                .map(Holder::value)
                .map(BuiltInRegistries.POTION::getKey)
                .orElse(null);

        double base = Configs.ALCHEMY.defaultBaseXp.get();
        if (potionId != null) {
            for (AlchemyRecipeXp entry : Configs.ALCHEMY.recipes.get()) {
                if (potionId.equals(entry.recipe.get())) {
                    base = entry.baseXp.get();
                    break;
                }
            }
        }

        int amplifier = 0;
        for (MobEffectInstance eff : contents.getAllEffects()) {
            amplifier = Math.max(amplifier, eff.getAmplifier());
        }

        double mult = Configs.ALCHEMY.amplifierMultiplier.evalSafe(
                Map.of('a', (double) amplifier), 1.0);
        return Math.max(0.0, base * mult);
    }
}
