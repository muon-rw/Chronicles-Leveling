package dev.muon.chronicles_leveling.skill.social;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.SpeechSkill;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Optional;

/**
 * Loader-agnostic Speech trade hooks, read by the common villager / merchant mixins. Server-thread only; every
 * entry point resolves the relevant player and no-ops when it is absent or unskilled.
 */
public final class SpeechTradeHandler {

    private SpeechTradeHandler() {}

    private static ServerPlayer trader(AbstractVillager villager) {
        return villager.getTradingPlayer() instanceof ServerPlayer player ? player : null;
    }

    /** Haggler (sell side): fewer emeralds on emerald-cost offers; rides vanilla's special-price reset on close. */
    public static void applyHagglerDiscount(Villager villager, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        double discount = SkillEffects.get(serverPlayer, SpeechSkill.TRADE_DISCOUNT);
        if (discount <= 0) {
            return;
        }
        for (MerchantOffer offer : villager.getOffers()) {
            if (!offer.getBaseCostA().is(Items.EMERALD)) {
                continue;
            }
            int cut = Math.round((float) (discount * offer.getBaseCostA().getCount()));
            if (cut > 0) {
                offer.addToSpecialPriceDiff(-cut);
            }
        }
    }

    /** Haggler (buy side): extra emeralds when a completed trade pays out emeralds. */
    public static void applyHagglerBonus(AbstractVillager villager, MerchantOffer offer) {
        ServerPlayer player = trader(villager);
        if (player == null || !offer.getResult().is(Items.EMERALD)) {
            return;
        }
        double markup = SkillEffects.get(player, SpeechSkill.TRADE_DISCOUNT);
        int bonus = markup > 0 ? Math.round((float) (markup * offer.getResult().getCount())) : 0;
        if (bonus <= 0) {
            return;
        }
        ItemStack emeralds = new ItemStack(Items.EMERALD, bonus);
        if (!player.getInventory().add(emeralds)) {
            player.drop(emeralds, false);
        }
    }

    /**
     * Enchanted Trader + Power Broker: enrich a bought trade result, including already-enchanted gear and enchanted
     * books. Applied where the result slot is (re)assembled ({@code MerchantContainerMixin}), so both cursor pickup
     * and shift-click mass trades get it; the on-take seam misses shift-click, which empties the result before onTake
     * sees it. Seeded on the player + offer + use count so the previewed result is stable, but each completed trade
     * (uses increments) rolls fresh. Enchanted Trader adds extra random enchantments; Power Broker raises the level
     * of every enchantment present (never a max-level-1 one like Mending). Plain books are skipped.
     */
    public static void enchantOfferResult(ServerPlayer player, ItemStack result, MerchantOffer offer) {
        if (result.isEmpty() || result.is(Items.BOOK) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean enchantedBook = result.is(Items.ENCHANTED_BOOK);
        if (!enchantedBook && !result.has(DataComponents.ENCHANTABLE)) {
            return;
        }
        int extra = (int) Math.floor(SkillEffects.get(player, SpeechSkill.ENCHANTED_TRADER));
        int boost = (int) Math.floor(SkillEffects.get(player, SpeechSkill.ENCHANT_LEVEL_BOOST));
        if (extra <= 0 && boost <= 0) {
            return;
        }
        RandomSource random = RandomSource.create(offerSeed(player, offer));
        RegistryAccess registries = level.registryAccess();
        int enchantLevel = Configs.SKILLS.speech.enchantedTraderLevel.get();
        for (int i = 0; i < extra; i++) {
            if (enchantedBook) {
                addRandomBookEnchantment(result, random, enchantLevel, registries);
            } else {
                EnchantmentHelper.enchantItem(random, result, enchantLevel, registries, Optional.empty());
            }
        }
        if (boost > 0) {
            boostEnchantLevels(result, boost);
        }
    }

    /** Power Broker: raise every enchantment on the result by {@code boost} levels, skipping max-level-1 enchants (Mending, Silk Touch). */
    private static void boostEnchantLevels(ItemStack result, int boost) {
        EnchantmentHelper.updateEnchantments(result, mutable -> {
            for (Holder<Enchantment> enchantment : List.copyOf(mutable.keySet())) {
                if (enchantment.value().getMaxLevel() > 1) {
                    mutable.set(enchantment, mutable.getLevel(enchantment) + boost);
                }
            }
        });
    }

    /** Stable per-(player, offer, use count) seed: the previewed result holds steady, but each completed trade rolls fresh. */
    private static long offerSeed(ServerPlayer player, MerchantOffer offer) {
        long seed = player.getUUID().hashCode();
        seed = seed * 31 + offer.getResult().getItem().hashCode();
        seed = seed * 31 + offer.getXp();
        seed = seed * 31 + offer.getUses();
        return seed;
    }

    private static void addRandomBookEnchantment(ItemStack book, RandomSource random, int enchantLevel, RegistryAccess registries) {
        ItemStack rolled = EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), enchantLevel, registries, Optional.empty());
        ItemEnchantments rolledEnchants = rolled.get(DataComponents.STORED_ENCHANTMENTS);
        if (rolledEnchants == null) {
            return;
        }
        for (Holder<Enchantment> enchantment : rolledEnchants.keySet()) {
            book.enchant(enchantment, rolledEnchants.getLevel(enchantment));
        }
    }

    /** Master Negotiator: scale the villager profession XP a trade grants by the trader's bonus. */
    public static int boostVillagerXp(Villager villager, int baseXp) {
        return applyVillagerXpBonus(villager.getTradingPlayer(), baseXp);
    }

    /** Master Negotiator preview: the boosted XP the open trade screen should show for the staged offer; runs on both sides. */
    public static int boostFutureXp(Player trader, int baseFutureXp) {
        return applyVillagerXpBonus(trader, baseFutureXp);
    }

    private static int applyVillagerXpBonus(Player trader, int baseXp) {
        if (trader == null || baseXp <= 0) {
            return baseXp;
        }
        double bonus = SkillEffects.capabilityValue(trader, SpeechSkill.VILLAGER_XP_BONUS);
        return bonus > 0 ? Mth.ceil(baseXp * (1.0 + bonus)) : baseXp;
    }

    /**
     * Master Negotiator: push the villager's updated XP to the trader's open screen so the boosted progress fills live.
     * Vanilla only sends merchant offers on open, so without this the accumulated bonus shows only after reopening.
     */
    public static void resyncTradeProgress(Villager villager) {
        if (!(villager.getTradingPlayer() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof MerchantMenu)) {
            return;
        }
        if (SkillEffects.get(player, SpeechSkill.VILLAGER_XP_BONUS) <= 0) {
            return;
        }
        player.sendMerchantOffers(player.containerMenu.containerId, villager.getOffers(),
                villager.getVillagerData().level(), villager.getVillagerXp(),
                villager.showProgressBar(), villager.canRestock());
    }

    /** Silver Tongue: whether a completed trade consumes the offer's stock (false rolls a free use). */
    public static boolean consumesStock(AbstractVillager villager) {
        ServerPlayer player = trader(villager);
        if (player == null) {
            return true;
        }
        double chance = SkillEffects.get(player, SpeechSkill.NO_STOCK_CONSUME);
        return chance <= 0 || player.getRandom().nextDouble() >= Math.min(chance, 1.0);
    }

    /**
     * Reputation: whether a holder of the restock perk is near enough to keep this villager restocking eagerly.
     * Keyed on live proximity (not the villager's transient {@code lastTradedPlayer}, which is nulled every tick and
     * never saved) so it survives reloads and fires reliably when the autonomous WorkAtPoi restock evaluates.
     */
    public static boolean hasNearbyReputableTrader(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        double radius = Configs.SKILLS.speech.reputationNearbyRadius.get();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(villager) <= radius * radius && SkillEffects.has(player, SpeechSkill.BETTER_RESTOCK)) {
                return true;
            }
        }
        return false;
    }
}
