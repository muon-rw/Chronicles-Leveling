package dev.muon.chronicles_leveling.skill.enchant;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.catalog.EnchantingSkill;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Centralized enchanting-perk logic. The enchant-menu mixins stay thin and route every decision here, so the
 * vanilla {@code EnchantmentMenu} backend (both loaders) and the NeoForge-only {@code ApothEnchantmentMenu}
 * backend (Apothic-Enchanting overrides the vanilla flow) share one implementation.
 *
 * <p>Reads are side-safe. The enchanting screen calls {@code clickMenuButton} on BOTH the client (a
 * pre-send affordability gate, with a NULL container access so nothing is actually enchanted) and the server
 * (the authoritative enchant). So a Prodigy gate read must answer on either side: on the server it hits the
 * {@link SkillEffects} cache; on the client it falls back to the pure {@link SkillEffects#derive} over the
 * synced skill data. Both compute the same discount, so the client's clickability and the server's result
 * agree without any extra screen hook.
 */
public final class EnchantingPerks {

    private EnchantingPerks() {}

    /** Hard ceiling on the Prodigy discount so the requirement inflation can never divide by ~0. */
    private static final double MAX_DISCOUNT = 0.90;

    // --- Prodigy: lower the table's level requirement + the levels it deducts, NOT the power seed ---

    /**
     * Inflates the player's level as the table's affordability gate sees it, so a slot whose requirement is
     * {@code R} becomes usable at real level {@code R * (1 - discount)}. The cost passed to the enchant roll
     * is left untouched, so the output enchantment is unchanged.
     */
    public static int prodigyGateLevel(Player player, int realLevel) {
        double discount = prodigyDiscount(player);
        if (discount <= 0.0) {
            return realLevel;
        }
        return (int) Math.floor(realLevel / (1.0 - discount));
    }

    /**
     * Reduces the levels the table deducts on a successful enchant. Vanilla only deducts the slot index + 1
     * (1-3 levels), so this is a minor lever next to the requirement reduction above. (Apothic instead charges
     * raw XP via {@code getExpCostForSlot}; {@link #prodigyReducedCost} cuts that too.)
     */
    public static int prodigyLevelsTaken(Player player, int levels) {
        return prodigyReducedCost(player, levels);
    }

    /** Cuts an integer enchant cost (vanilla levels or Apothic XP points) by the Prodigy discount; floors at 0. */
    public static int prodigyReducedCost(Player player, int cost) {
        double discount = prodigyDiscount(player);
        if (discount <= 0.0) {
            return cost;
        }
        return Math.max(0, (int) Math.round(cost * (1.0 - discount)));
    }

    /**
     * The level a player actually needs to use a slot whose base requirement is {@code baseRequirement},
     * after Prodigy, for the table's UX (the reduced-requirement tooltip line + showing the slot enabled).
     * Defined as the smallest level that clears {@link #prodigyGateLevel}, so the number shown always matches
     * what the gate accepts even under floating-point drift.
     */
    public static int prodigyRequirement(Player player, int baseRequirement) {
        double discount = prodigyDiscount(player);
        if (discount <= 0.0 || baseRequirement <= 0) {
            return baseRequirement;
        }
        int needed = Math.max(1, (int) Math.ceil(baseRequirement * (1.0 - discount)));
        while (prodigyGateLevel(player, needed) < baseRequirement) {
            needed++;
        }
        return needed;
    }

    /** The vanilla (and Apothic) enchant-slot tooltip line both backends emit for an unmet level requirement. */
    private static final String VANILLA_REQUIREMENT_KEY = "container.enchant.level.requirement";

    /**
     * Rewrites an enchant slot's hover tooltip for Prodigy: drops the vanilla/Apothic
     * "{@value #VANILLA_REQUIREMENT_KEY}" line (it carries the un-reduced requirement and would otherwise
     * sit next to ours), then adds a single "Level Requirement: reduced (was base)" line, green when the
     * player already meets the reduced bar, red when they still fall short. No-op when Prodigy isn't reducing
     * this slot. Shared by the vanilla and Apothic screen mixins (both emit the same vanilla key).
     */
    public static void decorateRequirementTooltip(Player player, int baseRequirement, List<Component> tooltip) {
        int reduced = prodigyRequirement(player, baseRequirement);
        if (reduced >= baseRequirement) {
            return;
        }
        tooltip.removeIf(line -> line.getContents() instanceof TranslatableContents tc
                && VANILLA_REQUIREMENT_KEY.equals(tc.getKey()));
        ChatFormatting color = player.experienceLevel >= reduced ? ChatFormatting.GREEN : ChatFormatting.RED;
        tooltip.add(Component.translatable("tooltip.chronicles_leveling.enchant_requirement_reduced", reduced, baseRequirement)
                .withStyle(color));
    }

    private static double prodigyDiscount(Player player) {
        return Math.min(MAX_DISCOUNT, capability(player, EnchantingSkill.PRODIGY_LEVEL_DISCOUNT));
    }

    // --- Esoteric Enchanter: allow non-curse treasure enchantments at the table ---

    /**
     * Augments the enchanting-table candidate pool with non-curse treasure enchantments when the player has
     * Esoteric Enchanter. Vanilla's pool is the {@code in_enchanting_table} tag (already curse-free); this adds
     * the {@code treasure} tag minus the {@code curse} tag. Returns the original stream unchanged otherwise.
     */
    public static Stream<Holder<Enchantment>> esotericSource(Player player, RegistryAccess access,
                                                             Stream<Holder<Enchantment>> original) {
        if (!hasFlag(player, EnchantingSkill.ESOTERIC_ENCHANTER)) {
            return original;
        }
        Optional<HolderSet.Named<Enchantment>> treasure =
                access.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.TREASURE);
        if (treasure.isEmpty()) {
            return original;
        }
        Stream<Holder<Enchantment>> extra = treasure.get().stream().filter(holder -> !holder.is(EnchantmentTags.CURSE));
        return Stream.concat(original, extra).distinct();
    }

    // --- Unstable / Unlimited Power: bump rolled enchant levels past their max ---

    /**
     * Applies Unstable Power (a per-enchant chance equal to the player's Enchanting level, capped at 100%, for
     * +1) and Unlimited Power (a flat +1) to a table roll's levels, bypassing each enchantment's max, but never
     * for enchantments whose max level is 1 (Mending, Silk Touch, …). The two stack. Deterministic for a given
     * {@code seed} so a slot's clue preview matches its actual roll. Returns {@code rolled} unchanged when the
     * player holds neither perk.
     */
    public static List<EnchantmentInstance> applyPowerPerks(Player player, long seed, List<EnchantmentInstance> rolled) {
        if (rolled.isEmpty()) {
            return rolled;
        }
        boolean unlimited = hasFlag(player, EnchantingSkill.UNLIMITED_POWER);
        boolean unstable = hasFlag(player, EnchantingSkill.UNSTABLE_POWER);
        if (!unlimited && !unstable) {
            return rolled;
        }
        double unstableChance = unstable
                ? Math.min(1.0, PlayerSkillManager.get(player).get(Skills.ENCHANTING).level() / 100.0)
                : 0.0;
        RandomSource random = RandomSource.create(seed);
        List<EnchantmentInstance> result = new ArrayList<>(rolled.size());
        for (EnchantmentInstance instance : rolled) {
            if (instance.enchantment().value().getMaxLevel() <= 1) {
                result.add(instance);   // max-level-1 enchants (Mending, Silk Touch, …) are never bumped
                continue;
            }
            int bonus = (unlimited ? 1 : 0) + (unstable && random.nextFloat() < unstableChance ? 1 : 0);
            result.add(bonus == 0 ? instance : new EnchantmentInstance(instance.enchantment(), instance.level() + bonus));
        }
        return result;
    }

    // --- Abundance: roll more enchantments onto the gear (independent of rarity / Apothic Arcana) ---

    /** Distinct RNG stream from the seed so Abundance's coin-flips don't correlate with the Power-perk draws. */
    private static final long ABUNDANCE_SALT = 0x9E3779B97F4A7C15L;

    /** Number of Abundance trials (= perk rank); public so a backend can skip building its candidate pool without the perk. */
    public static int abundanceTrials(Player player) {
        return (int) Math.floor(capability(player, EnchantingSkill.ABUNDANCE_TRIALS));
    }

    /**
     * The vanilla candidate pool for Abundance: the {@code in_enchanting_table} tag (plus Esoteric's non-curse
     * treasure), narrowed to what the item can take at this cost. Empty without the perk. The Apothic backend
     * supplies its OWN pool instead (so the table blacklist / per-table treasure flag / cost windows are honored).
     */
    public static List<EnchantmentInstance> vanillaAbundanceCandidates(Player player, ItemStack item, int cost,
                                                                       RegistryAccess access) {
        if (abundanceTrials(player) <= 0 || item.isEmpty() || cost <= 0) {
            return List.of();
        }
        Optional<HolderSet.Named<Enchantment>> pool =
                access.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (pool.isEmpty()) {
            return List.of();
        }
        Stream<Holder<Enchantment>> source = esotericSource(player, access, pool.get().stream());
        return EnchantmentHelper.getAvailableEnchantmentResults(cost, item, source);
    }

    /**
     * Adds up to {@code rank} extra enchantments to a roll: one independent coin-flip per rank (chance from
     * config), each adding a weighted-random pick from {@code available} (the backend's own cost-windowed
     * candidate pool), skipping ones already rolled or incompatible. The COUNT is what grows; rarity weighting is
     * untouched, so this never steps on Apothic's Arcana (which biases <i>which</i> enchants, not <i>how many</i>).
     * Deterministic for a given {@code seed}, so the clue preview and the applied roll agree. Returns {@code rolled}
     * unchanged without the perk or with no candidates.
     */
    public static List<EnchantmentInstance> applyAbundance(Player player, long seed, List<EnchantmentInstance> rolled,
                                                           List<EnchantmentInstance> available) {
        int trials = abundanceTrials(player);
        if (trials <= 0 || available.isEmpty()) {
            return rolled;
        }
        List<EnchantmentInstance> candidates = new ArrayList<>(available);
        List<EnchantmentInstance> result = new ArrayList<>(rolled);
        for (EnchantmentInstance existing : rolled) {
            candidates.removeIf(candidate -> candidate.enchantment().equals(existing.enchantment()));
            EnchantmentHelper.filterCompatibleEnchantments(candidates, existing);
        }
        double chance = Configs.SKILLS.enchanting.abundanceChance.get();
        RandomSource random = RandomSource.create(seed ^ ABUNDANCE_SALT);
        for (int i = 0; i < trials && !candidates.isEmpty(); i++) {
            if (random.nextFloat() >= chance) {
                continue;
            }
            Optional<EnchantmentInstance> pick = WeightedRandom.getRandomItem(random, candidates, EnchantmentInstance::weight);
            if (pick.isEmpty()) {
                break;
            }
            EnchantmentInstance picked = pick.get();
            result.add(picked);
            candidates.removeIf(candidate -> candidate.enchantment().equals(picked.enchantment()));
            EnchantmentHelper.filterCompatibleEnchantments(candidates, picked);
        }
        return result;
    }

    // --- Experimenter: combine same-group damage / protection enchantments; anvil (rank 1) + table (rank 2); exclusivity only, not applicability ---

    /** Experimenter tier (= perk rank): 1 = anvil combine, 2 = also co-roll at the table. Side-safe. */
    public static int experimenterTier(Player player) {
        return (int) Math.floor(capability(player, EnchantingSkill.EXPERIMENTER_TIER));
    }

    /**
     * Experimenter: at the anvil, treats two normally-exclusive enchantments as compatible when both sit in the
     * same vanilla exclusive-set group ({@code damage} or {@code armor}), so a weapon can stack Sharpness/Smite/…
     * and armor can stack the Protections. Only the EXCLUSIVITY is relaxed; per-item applicability ({@code canEnchant})
     * is left to vanilla, so this never lets an enchant onto an item that can't hold it. False without the perk.
     */
    public static boolean canCombineExclusive(Player player, Holder<Enchantment> first, Holder<Enchantment> second) {
        return experimenterTier(player) >= 1 && sameExclusiveGroup(first, second);
    }

    /** Experimenter rank 2: whether same-group damage/protection enchants may co-roll at the enchanting table for this player. */
    public static boolean relaxesTableExclusivity(Player player) {
        return experimenterTier(player) >= 2;
    }

    /** Both enchantments sit in the same vanilla exclusive-set group ({@code damage} or {@code armor}). */
    public static boolean sameExclusiveGroup(Holder<Enchantment> first, Holder<Enchantment> second) {
        return (first.is(EnchantmentTags.DAMAGE_EXCLUSIVE) && second.is(EnchantmentTags.DAMAGE_EXCLUSIVE))
                || (first.is(EnchantmentTags.ARMOR_EXCLUSIVE) && second.is(EnchantmentTags.ARMOR_EXCLUSIVE));
    }

    /**
     * Scoped flag, read by the common {@code EnchantmentMixin} gate over {@code Enchantment.areCompatible}: true only
     * inside a table roll the enchanting-table mixins opened for a rank-2 Experimenter, so every other compatibility
     * check (the anvil, tooltips, gameplay) stays strict. The anvil combine is handled at its own call site instead.
     */
    private static final ThreadLocal<Boolean> TABLE_EXCLUSIVITY_RELAXED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isTableExclusivityRelaxed() {
        return TABLE_EXCLUSIVITY_RELAXED.get();
    }

    /** The table mixins set true before a roll and false (in a finally) after, so the relaxation never leaks past one roll. */
    public static void setTableExclusivityRelaxed(boolean relaxed) {
        if (relaxed) {
            TABLE_EXCLUSIVITY_RELAXED.set(Boolean.TRUE);
        } else {
            TABLE_EXCLUSIVITY_RELAXED.remove();
        }
    }

    // --- Arcane Insight: reveal more of a slot's would-be enchantments (client preview) ---

    /** The number of extra enchantment clues to reveal (perk rank); {@code >= 3} means reveal them all. */
    public static int arcaneInsightReveal(Player player) {
        return (int) Math.floor(capability(player, EnchantingSkill.ARCANE_INSIGHT_REVEAL));
    }

    /** Side-safe Esoteric Enchanter check; used by the Apothic backend, which forces {@code treasure} table stats. */
    public static boolean hasEsoteric(Player player) {
        return hasFlag(player, EnchantingSkill.ESOTERIC_ENCHANTER);
    }

    // --- Transcribe (grindstone): copy an item's enchantments onto a book ---

    /**
     * Transcribe: when an enchanted item sits in the grindstone's top slot and a plain book in the second
     * slot, produce an enchanted book carrying half (rank 1, rounded up) or all (rank 2+) of the item's
     * non-curse enchantments. Curses stay with the item (grindstone semantics). Returns {@code EMPTY} when this
     * isn't a Transcribe operation, so vanilla's result computation runs.
     */
    public static ItemStack transcribeResult(Player player, ItemStack input, ItemStack additional) {
        int tier = transcribeTier(player);
        if (tier <= 0 || !additional.is(Items.BOOK) || input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<Holder<Enchantment>> transferable = transcribable(input);
        if (transferable.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int take = tier == 1 ? (transferable.size() + 1) / 2 : transferable.size();
        ItemEnchantments source = input.getEnchantments();
        ItemEnchantments.Mutable onBook = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (int i = 0; i < take; i++) {
            Holder<Enchantment> holder = transferable.get(i);
            onBook.set(holder, source.getLevel(holder));
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, onBook.toImmutable());
        return book;
    }

    /**
     * Transcribe rank 3: when the player takes the transcribed book, hand back the source item (stripped of
     * the non-curse enchantments that were just extracted) to their inventory (dropped if full). Lower ranks
     * consume the item. Called from the grindstone result slot's take, before the input slot is cleared.
     */
    public static void preserveTranscribedItem(Player player, Container repairSlots) {
        if (transcribeTier(player) < 3) {
            return;
        }
        ItemStack additional = repairSlots.getItem(1);
        ItemStack input = repairSlots.getItem(0);
        if (!additional.is(Items.BOOK) || transcribable(input).isEmpty()) {
            return;
        }
        ItemStack stripped = input.copy();
        ItemEnchantments.Mutable kept = new ItemEnchantments.Mutable(stripped.getEnchantments());
        kept.removeIf(holder -> !holder.is(EnchantmentTags.CURSE));   // extracted non-curses leave; curses stay
        stripped.set(DataComponents.ENCHANTMENTS, kept.toImmutable());
        player.getInventory().placeItemBackInInventory(stripped);
    }

    private static int transcribeTier(Player player) {
        return player instanceof ServerPlayer serverPlayer
                ? (int) Math.floor(SkillEffects.get(serverPlayer, EnchantingSkill.TRANSCRIBE_TIER))
                : 0;
    }

    /** The item's non-curse enchantments: the set Transcribe can move to a book (curses are sticky). */
    private static List<Holder<Enchantment>> transcribable(ItemStack item) {
        return item.getEnchantments().keySet().stream()
                .filter(holder -> !holder.is(EnchantmentTags.CURSE))
                .toList();
    }

    /** Reads a boolean (flag) enchanting capability on either logical side (server cache / client derive). */
    private static boolean hasFlag(Player player, SkillCapability<Boolean> capability) {
        if (player instanceof ServerPlayer serverPlayer) {
            return SkillEffects.has(serverPlayer, capability);
        }
        Object value = SkillEffects.derive(player).capabilities().getOrDefault(capability, capability.absent());
        return Boolean.TRUE.equals(value);
    }

    /** Reads an additive (Double) enchanting capability on either logical side. */
    private static double capability(Player player, SkillCapability<Double> capability) {
        if (player instanceof ServerPlayer serverPlayer) {
            return SkillEffects.get(serverPlayer, capability);
        }
        Object value = SkillEffects.derive(player).capabilities().getOrDefault(capability, capability.absent());
        return value instanceof Double d ? d : 0.0;
    }
}
