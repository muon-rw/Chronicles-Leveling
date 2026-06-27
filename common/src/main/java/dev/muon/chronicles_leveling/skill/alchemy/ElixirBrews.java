package dev.muon.chronicles_leveling.skill.alchemy;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Elixir distillation shared by the Experimental Elixir (beneficial drinkable) and Volatile Elixir (harmful
 * splash, lingering with Negation Mastery) actives. Consumes one reagent from the main hand: a glass bottle,
 * water bottle, or awkward potion. The effect pool is the whole MobEffect registry filtered to the requested
 * category minus the config blacklist; the count is the root perk's rank (1..3). School/mastery amplifier
 * bonuses bake directly into the rolled effects (no base potion identity to preserve), and Lingering Touch
 * rides {@code POTION_DURATION_SCALE} like a brewed potion.
 */
public final class ElixirBrews {

    private ElixirBrews() {}

    public static final int MAX_EFFECTS = 3;
    private static final int MIN_DURATION = 600;     // 30s
    private static final int DURATION_SPREAD = 3000; // up to +2.5min

    public static boolean holdingReagent(ServerPlayer player) {
        return isReagent(player.getMainHandItem());
    }

    public static boolean isReagent(ItemStack stack) {
        if (stack.is(Items.GLASS_BOTTLE)) {
            return true;
        }
        if (!stack.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(Potions.WATER) || contents.is(Potions.AWKWARD));
    }

    /** Rolls an elixir of the given category and hands it to the player; false if no reagent or empty pool. */
    public static boolean brew(ServerPlayer player, MobEffectCategory category) {
        ItemStack reagent = player.getMainHandItem();
        if (!isReagent(reagent)) {
            return false;
        }
        List<Holder<MobEffect>> pool = pool(category);
        if (pool.isEmpty()) {
            return false;
        }

        RandomSource random = player.getRandom();
        int rank = PlayerSkillManager.get(player).get(Skills.ALCHEMY).rankOf(AlchemySkill.EXPERIMENTAL_ELIXIR_PERK);
        int count = Math.min(pool.size(), Mth.clamp(rank, 1, MAX_EFFECTS));
        int boost = categoryBoost(player, category, random);

        List<MobEffectInstance> effects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Holder<MobEffect> effect = pool.remove(random.nextInt(pool.size()));   // distinct: drawn without replacement
            int amplifier = Math.min(MobEffectInstance.MAX_AMPLIFIER, random.nextInt(2) + boost);
            int duration = effect.value().isInstantenous() ? 1 : MIN_DURATION + random.nextInt(DURATION_SPREAD + 1);
            effects.add(new MobEffectInstance(effect, duration, amplifier));
        }

        boolean harmful = category == MobEffectCategory.HARMFUL;
        Item form = harmful
                ? (SkillEffects.has(player, AlchemySkill.NEGATION_MASTERY) ? Items.LINGERING_POTION : Items.SPLASH_POTION)
                : Items.POTION;
        String nameToken = harmful ? "chronicles_volatile" : "chronicles_experimental";
        ItemStack elixir = new ItemStack(form);
        elixir.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), effects, Optional.of(nameToken)));
        double lingering = Math.max(0.0, SkillEffects.get(player, AlchemySkill.LINGERING_TOUCH));
        if (lingering > 0.0) {
            elixir.set(DataComponents.POTION_DURATION_SCALE, (float) (1.0 + lingering));
        }

        // Master Brewer extends to distilling: a chance to yield a double stack of the SAME elixir (not a re-roll).
        if (random.nextDouble() < Math.min(1.0, SkillEffects.get(player, AlchemySkill.EXTRA_BREW_CHANCE))) {
            elixir.setCount(2);
        }

        reagent.shrink(1);
        if (!player.getInventory().add(elixir)) {
            player.drop(elixir, false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 1.0f);
        return true;
    }

    private static List<Holder<MobEffect>> pool(MobEffectCategory category) {
        List<? extends String> blacklist = Configs.SKILLS.alchemy.elixirEffectBlacklist.get();
        List<Holder<MobEffect>> pool = new ArrayList<>();
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            if (effect.getCategory() != category) {
                continue;
            }
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id == null || blacklist.contains(id.toString())) {
                continue;
            }
            pool.add(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        }
        return pool;
    }

    /** One school roll per distillation (chance equal to the Alchemy level), plus the unconditional mastery +1. */
    private static int categoryBoost(ServerPlayer player, MobEffectCategory category, RandomSource random) {
        boolean restoration = category == MobEffectCategory.BENEFICIAL;
        boolean school = SkillEffects.has(player,
                restoration ? AlchemySkill.RESTORATION_SCHOOL : AlchemySkill.NEGATION_SCHOOL);
        boolean mastery = SkillEffects.has(player,
                restoration ? AlchemySkill.RESTORATION_MASTERY : AlchemySkill.NEGATION_MASTERY);
        double chance = Math.min(1.0, PlayerSkillManager.get(player).get(Skills.ALCHEMY).level() / 100.0);
        return (school && random.nextDouble() < chance ? 1 : 0) + (mastery ? 1 : 0);
    }
}
