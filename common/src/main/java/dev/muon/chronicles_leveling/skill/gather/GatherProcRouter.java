package dev.muon.chronicles_leveling.skill.gather;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.mixin.CropBlockInvoker;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.SkillEnchants;
import dev.muon.chronicles_leveling.skill.SkillTags;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import dev.muon.chronicles_leveling.skill.catalog.HerbalismSkill;
import dev.muon.chronicles_leveling.skill.catalog.MiningSkill;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The loot/yield sibling of {@link dev.muon.chronicles_leveling.skill.combat.CombatProcRouter}, for the
 * gathering trees. It reads {@link SkillEffects} capabilities a player earned in Mining / Herbalism and routes
 * the gathering procs: most mutate a block break's drops <em>before they spawn</em> ({@link #modifyDrops}:
 * re-rolling with Fortune, smelting, appending bonus/pool drops, replanting), and one is a non-drop side
 * effect on planting ({@link #greenThumbAfterPlant} auto-bonemeals a just-planted crop).
 *
 * <p>Driven loader-split (both feed {@link #modifyDrops}): NeoForge subscribes to {@code BlockDropsEvent}
 * (the patch-proof seam), Fabric wraps the {@code getDrops} call inside {@code Block#dropResources} (vanilla
 * there). A pre-spawn seam, rather than the simpler post-break TAIL, is required so REPLACE procs (Smelter's
 * Touch) work, not just additive ones. Server-thread only; no transient state.
 *
 * <p><b>Functional (drops, via {@link #modifyDrops}):</b> Natural Fortune, Smelter's Touch, Mother Lode,
 * Gem Hunter, Cultivation, Auto-Replant, Rupee Farmer, Toxin Harvest, Mycologist. <b>Functional (non-drop):</b>
 * Green Thumb ({@link #greenThumbAfterPlant}, driven from the plant seams and from Auto-Replant). <b>Other-seam:</b>
 * Sturdy Tools / tool durability ({@code ItemMixin} on {@code Item#mineBlock}), Gardener's Infusion (craft/cook
 * stamping), Bountiful Harvest (active ability), and all Fishing/Smithing/Enchanting perks hook elsewhere.
 */
public final class GatherProcRouter {

    private GatherProcRouter() {}

    // Loot pools resolved from config (reparsed only when the config list instance changes). Adding another
    // pooled perk is one more LootPool field, no copy-pasted cache triplet.
    private static final LootPool RUPEE_POOL = new LootPool(() -> Configs.SKILLS.herbalism.rupeeFarmerLoot.get());
    private static final LootPool TOXIN_POOL = new LootPool(() -> Configs.SKILLS.herbalism.toxinHarvestLoot.get());

    /**
     * Applies the gathering loot perks to a player's block-break drops, mutating {@code drops} in place, and
     * replants a harvested crop as a side effect. Returns whether the drop list changed (so the NeoForge
     * adapter can skip rebuilding {@code ItemEntity}s when nothing did).
     */
    public static boolean modifyDrops(ServerPlayer player, ServerLevel level, BlockPos pos,
                                      BlockState state, ItemStack tool, List<ItemStack> drops) {
        RandomSource rng = player.getRandom();
        boolean changed = false;

        boolean ore = state.is(SkillTags.ORES);
        boolean crop = state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state);
        boolean foliage = !(state.getBlock() instanceof CropBlock)
                && (state.getBlock() instanceof VegetationBlock || state.is(BlockTags.LEAVES));

        // Natural Fortune: re-roll the ore's drops as if the tool carried +N Fortune (a baseline Fortune
        // without the enchant; runs first so Smelter's Touch then smelts the fortune'd raw). Silk Touch wins
        // inside the re-roll, so it's a no-op for a silk tool.
        if (ore) {
            int fortune = (int) Math.floor(SkillEffects.get(player, MiningSkill.NATURAL_FORTUNE));
            if (fortune > 0) {
                drops.clear();
                drops.addAll(Block.getDrops(state, level, pos, null, player,
                        SkillEnchants.fortuneBoosted(player, tool, fortune)));
                changed = true;
            }
        }

        // Smelter's Touch (active): while the window is open, replace each smeltable ore drop with its furnace
        // result. Silk Touch wins: it means the player wanted the intact block (and a few ore BLOCKS have a
        // furnace recipe), so don't smelt then.
        if (ore && AbilityWindowStore.isActive(player, AbilityWindowStore.WindowKind.SMELTERS_TOUCH)
                && !SkillEnchants.hasSilkTouch(player, tool)) {
            for (int i = 0; i < drops.size(); i++) {
                ItemStack smelted = smelt(drops.get(i), level);
                if (smelted != null) {
                    drops.set(i, smelted);
                    changed = true;
                }
            }
        }

        // Post-smelt snapshot the additive perks duplicate, so bonus drops inherit the smelt.
        List<ItemStack> base = List.copyOf(drops);

        if (ore) {
            if (rolls(player, MiningSkill.EXTRA_ORE_DROP, rng)) {
                changed |= addCopies(drops, base);                                              // Mother Lode
            }
            if ((state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES))
                    && rolls(player, MiningSkill.RARE_ORE_BONUS, rng)) {
                changed |= addCopies(drops, base);                                              // Gem Hunter
            }
        }
        if (crop) {
            CropBlock cropBlock = (CropBlock) state.getBlock();
            if (rolls(player, HerbalismSkill.EXTRA_CROP_YIELD, rng)) {
                changed |= addCopies(drops, base);                                              // Cultivation
            }
            // Auto-Replant: replant at age 0, but only if the player carries (and spends) a matching seed; guard
            // canSurvive so we don't force a crop where it can't live (farmland gone the same tick).
            if (SkillEffects.has(player, HerbalismSkill.AUTO_REPLANT)) {
                BlockState replant = cropBlock.getStateForAge(0);
                if (replant.canSurvive(level, pos) && consumeSeed(player, cropBlock)) {
                    level.setBlockAndUpdate(pos, replant);
                    greenThumbAfterPlant(player, level, pos);                                    // Green Thumb
                }
            }
        }
        if (foliage && rolls(player, HerbalismSkill.RUPEE_FARMER, rng)) {
            changed |= addPoolItem(drops, RUPEE_POOL.resolve(), rng);                            // Rupee Farmer
        }
        if ((crop || foliage) && rolls(player, HerbalismSkill.TOXIN_HARVEST, rng)) {
            changed |= addPoolItem(drops, TOXIN_POOL.resolve(), rng);                            // Toxin Harvest
        }
        // Mycologist (rank 3): breaking a fungal block has a chance for an extra full yield of its drops.
        if (isFungal(state) && MycologyHandler.rank(player) >= 3
                && rng.nextDouble() < Math.min(Configs.SKILLS.herbalism.mycologistFungalDropChance.get(), 1.0)) {
            changed |= addCopies(drops, base);
        }
        return changed;
    }

    /** Fungal blocks Mycologist's drop bonus applies to: mushrooms, nether fungi, huge mushrooms, and nether wart. */
    private static boolean isFungal(BlockState state) {
        Block block = state.getBlock();
        return block instanceof MushroomBlock || block instanceof NetherFungusBlock
                || block instanceof HugeMushroomBlock || block instanceof NetherWartBlock;
    }

    /**
     * Herbalism "Green Thumb": after a crop is planted (manually or by Auto-Replant), a chance equal to the
     * player's Herbalism level auto-bonemeals it forward a rank-scaled number of growth stages. No-op on
     * non-crop plantings (saplings, berries).
     */
    public static void greenThumbAfterPlant(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var entry = PlayerSkillManager.get(player).get(Skills.HERBALISM);
        int rank = entry.rankOf("green_thumb");
        if (rank <= 0) {
            return;
        }
        double chance = Math.min(1.0, entry.level() * Configs.SKILLS.herbalism.greenThumbChancePerLevel.get());
        if (player.getRandom().nextDouble() >= chance) {
            return;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof CropBlock crop)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        int stages = rank * Configs.SKILLS.herbalism.greenThumbStagesPerRank.get();
        BlockState grown = state;
        for (int i = 0; i < stages && !crop.isMaxAge(grown); i++) {
            grown = crop.getStateForAge(crop.getAge(grown) + 1);
        }
        if (grown != state) {
            level.setBlockAndUpdate(pos.immutable(), grown);
        }
    }

    /**
     * Auto-Replant cost: spend one of the crop's seeds from the player's inventory, returning whether a seed was
     * found. TODO: compat for backpack/bundle mods (Sophisticated Backpacks, vanilla bundles) by scanning nested
     * containers before giving up.
     */
    private static boolean consumeSeed(ServerPlayer player, CropBlock crop) {
        Item seed = ((CropBlockInvoker) crop).chronicles_leveling$getBaseSeedId().asItem();
        if (seed == Items.AIR) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(seed)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /**
     * Applies the crop-harvest drop perks (Cultivation extra yield, Toxin Harvest reagent) to a set of drops,
     * mutating {@code drops} in place. Used by the Bountiful Harvest ability, which harvests crops outside the normal
     * block-break pipeline but should still honor the holder's earned harvest bonuses.
     */
    public static void applyCropHarvestBonuses(ServerPlayer player, List<ItemStack> drops) {
        RandomSource rng = player.getRandom();
        List<ItemStack> base = List.copyOf(drops);
        if (rolls(player, HerbalismSkill.EXTRA_CROP_YIELD, rng)) {
            addCopies(drops, base);
        }
        if (rolls(player, HerbalismSkill.TOXIN_HARVEST, rng)) {
            addPoolItem(drops, TOXIN_POOL.resolve(), rng);
        }
    }

    private static boolean rolls(ServerPlayer player, SkillCapability<Double> capability, RandomSource rng) {
        double chance = SkillEffects.get(player, capability);
        return chance > 0 && rng.nextDouble() < Math.min(chance, 1.0);
    }

    /** Appends a fresh copy of every base drop: an extra full yield of what the break produced. */
    private static boolean addCopies(List<ItemStack> drops, List<ItemStack> base) {
        boolean any = false;
        for (ItemStack stack : base) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
                any = true;
            }
        }
        return any;
    }

    private static boolean addPoolItem(List<ItemStack> drops, List<Item> pool, RandomSource rng) {
        if (pool.isEmpty()) {
            return false;
        }
        drops.add(new ItemStack(pool.get(rng.nextInt(pool.size()))));
        return true;
    }

    /** The furnace result for a stack (count-scaled), or {@code null} if it doesn't smelt. */
    private static ItemStack smelt(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) {
            return null;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> holder.value().assemble(input))
                .filter(result -> !result.isEmpty())
                // Scale by the input count, clamped to the result's max stack so a multi-output recipe or a big
                // fortune'd stack can't pop a single overstacked entity.
                .map(result -> result.copyWithCount(
                        Math.min(result.getCount() * stack.getCount(), result.getMaxStackSize())))
                .orElse(null);
    }

    /** A config-backed item pool, reparsed only when the underlying config list instance changes. */
    private static final class LootPool {
        private final Supplier<List<? extends String>> config;
        private List<? extends String> cachedSource;
        private List<Item> pool = List.of();

        LootPool(Supplier<List<? extends String>> config) {
            this.config = config;
        }

        List<Item> resolve() {
            List<? extends String> source = config.get();
            if (source != cachedSource) {
                pool = parse(source);
                cachedSource = source;
            }
            return pool;
        }

        private static List<Item> parse(List<? extends String> ids) {
            List<Item> items = new ArrayList<>();
            for (String id : ids) {
                if (id == null || id.isEmpty()) {
                    continue;
                }
                Identifier itemId = Identifier.tryParse(id);
                if (itemId != null) {
                    BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(items::add);
                }
            }
            return List.copyOf(items);
        }
    }
}
