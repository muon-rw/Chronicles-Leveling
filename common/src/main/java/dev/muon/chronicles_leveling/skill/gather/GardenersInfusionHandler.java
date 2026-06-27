package dev.muon.chronicles_leveling.skill.gather;

import dev.muon.chronicles_leveling.component.GardenersInfusion;
import dev.muon.chronicles_leveling.component.ModComponents;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Gardener's Infusion: when a perk holder crafts or cooks food, bake the rank's boost straight into the stack's
 * vanilla FOOD (and, at rank 3, CONSUMABLE) components, then stamp a {@link GardenersInfusion} marker. Baking the
 * real FoodProperties is what keeps it compatible with vanilla hunger, Combat-Attributes Legacy Hunger, and AppleSkin,
 * all of which read FoodProperties rather than any custom component. The marker also makes the bake idempotent so a
 * re-craft into an already-infused stack does not stack the boost.
 *
 * <p>Tiers (perk rank): 1 raises hunger; 2 also raises saturation; 3 also doubles positive-effect durations.
 */
public final class GardenersInfusionHandler {

    private GardenersInfusionHandler() {}

    public static boolean isInfused(ItemStack stack) {
        return stack.has(ModComponents.GARDENERS_INFUSION);
    }

    /** The "Gardener's Infusion" line shown below an infused food's name. */
    public static Component infusedLine() {
        return Component.translatable("tooltip.chronicles_leveling.gardeners_infused").withStyle(ChatFormatting.GREEN);
    }

    public static void infuse(ServerPlayer crafter, ItemStack result) {
        if (crafter == null || result.isEmpty() || !result.has(DataComponents.FOOD)
                || result.has(ModComponents.GARDENERS_INFUSION)) {
            return;
        }
        int tier = PlayerSkillManager.get(crafter).get(Skills.HERBALISM).rankOf("gardeners_infusion");
        if (tier <= 0) {
            return;
        }
        var h = Configs.SKILLS.herbalism;
        FoodProperties food = result.get(DataComponents.FOOD);
        double saturationMult = tier >= 2 ? h.gardenersInfusionSaturationMultiplier.get() : 1.0;
        result.set(DataComponents.FOOD, new FoodProperties(
                Math.max(1, Mth.ceil(food.nutrition() * h.gardenersInfusionHungerMultiplier.get())),
                (float) (food.saturation() * saturationMult),
                food.canAlwaysEat()));
        if (tier >= 3) {
            doublePositiveEffectDurations(result, h.gardenersInfusionEffectDurationMultiplier.get());
        }
        result.set(ModComponents.GARDENERS_INFUSION, new GardenersInfusion(tier));
    }

    private static void doublePositiveEffectDurations(ItemStack result, double durationMult) {
        Consumable consumable = result.get(DataComponents.CONSUMABLE);
        if (consumable == null || durationMult == 1.0) {
            return;
        }
        List<ConsumeEffect> rebuilt = new ArrayList<>();
        boolean changed = false;
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect applied)) {
                rebuilt.add(effect);
                continue;
            }
            List<MobEffectInstance> scaled = new ArrayList<>();
            for (MobEffectInstance instance : applied.effects()) {
                scaled.add(stretchBeneficial(instance, durationMult));
            }
            rebuilt.add(new ApplyStatusEffectsConsumeEffect(scaled, applied.probability()));
            changed = true;
        }
        if (changed) {
            result.set(DataComponents.CONSUMABLE, new Consumable(consumable.consumeSeconds(),
                    consumable.animation(), consumable.sound(), consumable.hasConsumeParticles(), rebuilt));
        }
    }

    private static MobEffectInstance stretchBeneficial(MobEffectInstance instance, double durationMult) {
        if (instance.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL
                || instance.getEffect().value().isInstantenous() || instance.isInfiniteDuration()) {
            return instance;
        }
        return new MobEffectInstance(instance.getEffect(),
                Math.max(1, (int) (instance.getDuration() * durationMult)),
                instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon());
    }
}
