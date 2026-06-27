package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.client.enchant.ArcaneInsightClues;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Client-side UX for Prodigy's enchant-table level-requirement reduction: a slot the perk brings within
 * reach renders as <i>enabled/clickable</i> (not just functionally clickable), and its hover tooltip gains a
 * "Level Requirement: reduced (was base)" line. The functional gate lives in {@link EnchantmentMenuMixin}
 * routed through {@code EnchantingPerks}; this only mirrors that same decision in the view, so the slot's look
 * and the click never disagree.
 *
 * <p>The screen reads {@code minecraft.player.experienceLevel} as {@code LocalPlayer.experienceLevel} (its
 * static type), so the field targets below name {@code LocalPlayer}, unlike the menu mixin's {@code Player}.
 * The {@code extends} clause is compiler-only (mirrors {@code InventoryScreenMixin}); at runtime the mixin
 * merges into {@link EnchantmentScreen}, exposing {@code menu}, {@code minecraft}, and {@code isHovering}.
 */
@Mixin(value = EnchantmentScreen.class, remap = false)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {

    private EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @ModifyExpressionValue(method = "extractBackground",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
                    opcode = Opcodes.GETFIELD),
            remap = false)
    private int chronicles_leveling$prodigyEnableSlot(int realLevel) {
        return EnchantingPerks.prodigyGateLevel(this.minecraft.player, realLevel);
    }

    @ModifyExpressionValue(method = "extractRenderState",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
                    opcode = Opcodes.GETFIELD),
            remap = false)
    private int chronicles_leveling$prodigyTooltipAfford(int realLevel) {
        return EnchantingPerks.prodigyGateLevel(this.minecraft.player, realLevel);
    }

    /**
     * Hover tooltip: replace the slot's requirement line with Prodigy's reduced one. Vanilla still adds its
     * own {@code container.enchant.level.requirement} line (with the un-reduced value) when the player is below
     * even the reduced bar, so the helper strips that and emits a single adaptive line.
     */
    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"),
            remap = false)
    private void chronicles_leveling$prodigyRequirementLine(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ignored,
            CallbackInfo ci, @Local List<Component> texts) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            int base = this.menu.costs[i];
            if (base > 0 && this.isHovering(60, 14 + 19 * i, 108, 17, mouseX, mouseY)) {
                EnchantingPerks.decorateRequirementTooltip(player, base, texts);
                chronicles_leveling$revealEnchants(player, i, texts);
                return;
            }
        }
    }

    /**
     * Arcane Insight: reveal the slot's <i>other</i> would-be enchantments beyond vanilla's single clue (one
     * more per rank, all at the top rank), directly below that clue and above the blank + cost lines. The lists
     * come from the server ({@link ArcaneInsightClues}); the client can't replay the roll because the data-slot
     * sync truncates the enchant seed to 16 bits.
     *
     * <p>When the reveal covers the <i>entire</i> roll, the player has full certainty, so the whole clue block is
     * rebuilt as plain enchant names without vanilla's "{@code %s . . . ?}" uncertainty suffix. A partial reveal
     * keeps that suffix (it still signals "there may be more you can't see") and skips the enchant already shown
     * as vanilla's clue so it isn't listed twice.
     */
    @Unique
    private void chronicles_leveling$revealEnchants(Player player, int slot, List<Component> texts) {
        int reveal = EnchantingPerks.arcaneInsightReveal(player);
        if (reveal <= 0 || texts.isEmpty() || this.minecraft.level == null) {
            return;
        }
        List<EnchantmentInstance> list = ArcaneInsightClues.get(this.menu.containerId, slot);
        if (list.isEmpty()) {
            return;
        }
        int budget = reveal >= 3 ? Integer.MAX_VALUE : reveal;
        if (budget >= list.size() - 1) {   // every enchant beyond vanilla's single clue is revealed: full certainty
            // Plain tooltip names: keep each enchant's NATURAL color (gray / curse-red / any over-level tint a
            // pack mod applies), rather than forcing one tone, so the clue reads exactly as the item tooltip would.
            texts.set(0, Enchantment.getFullname(list.getFirst().enchantment(), list.getFirst().level()));
            for (int i = 1; i < list.size(); i++) {
                texts.add(i, Enchantment.getFullname(list.get(i).enchantment(), list.get(i).level()));
            }
            return;
        }
        if (list.size() <= 1) {
            return;
        }
        // Recolor only the WRAPPER of vanilla's white first clue line to the tooltip tone (the enchant name arg
        // keeps its own color) so it matches the extra clue lines below.
        texts.set(0, texts.get(0).copy().withStyle(ChatFormatting.GRAY));
        var idMap = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
        int clueId = this.menu.enchantClue[slot];
        int clueLevel = this.menu.levelClue[slot];
        int shown = 0;
        int insertAt = 1;   // directly under vanilla's single clue (index 0)
        for (EnchantmentInstance enchantment : list) {
            if (shown >= budget) {
                break;
            }
            if (idMap.getId(enchantment.enchantment()) == clueId && enchantment.level() == clueLevel) {
                continue;   // already shown as vanilla's single clue
            }
            texts.add(insertAt++, Component.translatable("container.enchant.clue",
                    Enchantment.getFullname(enchantment.enchantment(), enchantment.level())).withStyle(ChatFormatting.GRAY));
            shown++;
        }
    }

}
