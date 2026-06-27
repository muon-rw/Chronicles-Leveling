package dev.muon.chronicles_leveling.skill.enchant;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.EnchantingSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.EnchantingTableBlock;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Wizard's Study (Enchanting), non-render parts. Tracks how often the player enchants at each table (a
 * persistent {@link TableUsageData} attachment) and, when they enchant at their most-used table, bakes a
 * permanent +10% base-stat bonus into the result. The table glow is a separate (deferred) client renderer.
 *
 * <p>The bonus is a literal {@code ADD_MULTIPLIED_BASE +0.10} per built-in attribute on the item, multiplying
 * the computed (base + flat-modifier) value, so it improves both e.g. attack damage and attack speed without
 * reading the item's own modifier signs. The sign is the attribute's vanilla {@code Attribute.sentiment}: a
 * stat where a higher value is worse (its increase styles {@link ChatFormatting#RED}) gets {@code -0.10}
 * instead. Sentiment is set at registration ({@code setSentiment}) and is a server-side core property on both
 * loaders, so this read is correct server-side; a mod attribute that never calls {@code setSentiment} keeps the
 * {@code POSITIVE} default and so always takes the {@code +0.10} side.
 */
public final class WizardsStudyHandler {

    private WizardsStudyHandler() {}

    private static final double BONUS = 0.10;
    private static final String MODIFIER_PREFIX = "wizards_study/";

    /**
     * On a successful table enchant: record the table use, drop any since-destroyed tables, then, if the player
     * has Wizard's Study and this is now their most-used table, bake the base-stat bonus into the enchanted item.
     */
    public static void onEnchant(ServerPlayer player, GlobalPos table, ItemStack enchanted) {
        TableUsageStore store = Services.PLATFORM.getTableUsageStore();
        TableUsageData updated = store.get(player).recordUse(table).pruned(pos -> isStillTable(player, pos));
        store.set(player, updated);

        boolean hasPerk = SkillEffects.has(player, EnchantingSkill.WIZARDS_STUDY);
        GlobalPos mostUsed = updated.mostUsed();
        if (hasPerk && table.equals(mostUsed)) {
            bakeBaseStatBonus(enchanted);
        }
        NetworkDispatcher.sendWizardsStudyTable(player, Optional.ofNullable(hasPerk ? mostUsed : null));
    }

    /** Push the current most-used table (or empty when the player lacks the perk) to the owning client's glow cache. */
    public static void syncTarget(ServerPlayer player) {
        GlobalPos mostUsed = null;
        if (SkillEffects.has(player, EnchantingSkill.WIZARDS_STUDY)) {
            TableUsageStore store = Services.PLATFORM.getTableUsageStore();
            TableUsageData data = store.get(player);
            TableUsageData pruned = data.pruned(pos -> isStillTable(player, pos));
            if (pruned != data) {
                store.set(player, pruned);
            }
            mostUsed = pruned.mostUsed();
        }
        NetworkDispatcher.sendWizardsStudyTable(player, Optional.ofNullable(mostUsed));
    }

    /**
     * Whether the recorded position still holds an enchanting table (vanilla or an Apothic subclass). Unprovable
     * cases keep the entry: an unloaded dimension or chunk can't confirm the block is gone, so we only prune when a
     * loaded block is confirmed to no longer be a table.
     */
    private static boolean isStillTable(ServerPlayer player, GlobalPos table) {
        ServerLevel level = player.level().getServer().getLevel(table.dimension());
        if (level == null || !level.isLoaded(table.pos())) {
            return true;
        }
        return level.getBlockState(table.pos()).getBlock() instanceof EnchantingTableBlock;
    }

    /** True if the item carries the Wizard's Study base-stat bonus (used for the "Magically Infused" tooltip line). */
    public static boolean isMagicallyInfused(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers != null && alreadyEnhanced(modifiers);
    }

    /** The "Magically Infused" tooltip line shown below an infused item's name. */
    public static Component magicallyInfusedLine() {
        return Component.translatable("tooltip.chronicles_leveling.magically_infused").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /**
     * Strips the Wizard's Study {@value #MODIFIER_PREFIX} modifiers (and so the "Magically Infused" marker) from an
     * item, restoring its base attribute modifiers. Used when an item is ground down: the magic was baked by the
     * enchant, so removing the enchants removes the infusion too. No-op on items that aren't infused.
     */
    public static ItemStack stripInfusion(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (current == null || !alreadyEnhanced(current)) {
            return stack;
        }
        List<ItemAttributeModifiers.Entry> kept = current.modifiers().stream()
                .filter(entry -> !(entry.modifier().id().getNamespace().equals(ChroniclesLeveling.MOD_ID)
                        && entry.modifier().id().getPath().startsWith(MODIFIER_PREFIX)))
                .toList();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(kept));
        return stack;
    }

    /** Adds the sentiment-signed +10% modifier to each built-in attribute on the item. Idempotent (won't stack). */
    private static void bakeBaseStatBonus(ItemStack stack) {
        ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (current == null || current.modifiers().isEmpty() || alreadyEnhanced(current)) {
            return;
        }
        ItemAttributeModifiers result = current;
        Set<Holder<Attribute>> seen = new HashSet<>();
        int index = 0;
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            Holder<Attribute> attribute = entry.attribute();
            if (!seen.add(attribute)) {
                continue;   // one bonus per attribute
            }
            boolean higherIsWorse = attribute.value().getStyle(true) == ChatFormatting.RED;
            double amount = higherIsWorse ? -BONUS : BONUS;
            Identifier id = ChroniclesLeveling.id(MODIFIER_PREFIX + index++);
            AttributeModifier modifier = new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            result = result.withModifierAdded(attribute, modifier, entry.slot());
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, result);
    }

    private static boolean alreadyEnhanced(ItemAttributeModifiers modifiers) {
        return modifiers.modifiers().stream().anyMatch(entry ->
                entry.modifier().id().getNamespace().equals(ChroniclesLeveling.MOD_ID)
                        && entry.modifier().id().getPath().startsWith(MODIFIER_PREFIX));
    }
}
