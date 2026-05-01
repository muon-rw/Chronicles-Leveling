package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.ConfigSkills;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla fishing fires three distinct loot tables (fish/junk/treasure) but
 * neither {@code FishingHook.retrieve} nor NeoForge's {@code ItemFishedEvent}
 * exposes which one ran, so each caught item is matched against the configured
 * fish/treasure entry lists; whatever falls through earns
 * {@link ConfigSkills.Fishing#junkXp}. Entries are either item ids or tag ids
 * prefixed with {@code #}.
 */
public final class FishingXpHandler {

    private FishingXpHandler() {}

    private static List<? extends String> cachedFishSource;
    private static List<? extends String> cachedTreasureSource;
    private static EntryMatcher cachedFishMatcher = EntryMatcher.EMPTY;
    private static EntryMatcher cachedTreasureMatcher = EntryMatcher.EMPTY;

    public static void onItemFished(ServerPlayer player, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return;
        ConfigSkills.Fishing cfg = Configs.SKILLS.fishing;
        EntryMatcher fish = matcher(cfg.fishItems.get(), m -> {
            cachedFishMatcher = m;
            cachedFishSource = cfg.fishItems.get();
        }, cachedFishSource, cachedFishMatcher);
        EntryMatcher treasure = matcher(cfg.treasureItems.get(), m -> {
            cachedTreasureMatcher = m;
            cachedTreasureSource = cfg.treasureItems.get();
        }, cachedTreasureSource, cachedTreasureMatcher);

        double fishXp = cfg.fishXp.get();
        double treasureXp = cfg.treasureXp.get();
        double junkXp = cfg.junkXp.get();
        double total = 0.0;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (treasure.matches(drop))   total += treasureXp;
            else if (fish.matches(drop))  total += fishXp;
            else                          total += junkXp;
        }
        PlayerSkillManager.grantXp(player, Skills.FISHING, total);
    }

    private static EntryMatcher matcher(List<? extends String> source,
                                        java.util.function.Consumer<EntryMatcher> writeBack,
                                        List<? extends String> cachedSource,
                                        EntryMatcher cached) {
        if (source == cachedSource) return cached;
        EntryMatcher built = EntryMatcher.parse(source);
        writeBack.accept(built);
        return built;
    }

    private record EntryMatcher(List<Identifier> itemIds, List<TagKey<Item>> tags) {

        static final EntryMatcher EMPTY = new EntryMatcher(List.of(), List.of());

        static EntryMatcher parse(List<? extends String> entries) {
            List<Identifier> ids = new ArrayList<>();
            List<TagKey<Item>> tags = new ArrayList<>();
            for (String entry : entries) {
                if (entry == null || entry.isEmpty()) continue;
                if (entry.startsWith("#")) {
                    Identifier tagId = Identifier.tryParse(entry.substring(1));
                    if (tagId != null) tags.add(TagKey.create(Registries.ITEM, tagId));
                } else {
                    Identifier itemId = Identifier.tryParse(entry);
                    if (itemId != null) ids.add(itemId);
                }
            }
            return new EntryMatcher(List.copyOf(ids), List.copyOf(tags));
        }

        boolean matches(ItemStack stack) {
            if (!itemIds.isEmpty()) {
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null && itemIds.contains(id)) return true;
            }
            for (TagKey<Item> tag : tags) {
                if (stack.is(tag)) return true;
            }
            return false;
        }
    }
}
