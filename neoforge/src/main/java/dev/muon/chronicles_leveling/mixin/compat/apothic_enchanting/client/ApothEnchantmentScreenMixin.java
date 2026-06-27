package dev.muon.chronicles_leveling.mixin.compat.apothic_enchanting.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Apothic-Enchanting screen UX for Prodigy (NeoForge-only, gated). {@code ApothEnchantmentScreen} overrides
 * vanilla's {@code extract*} render, so the vanilla {@code EnchantmentScreenMixin} can't reach it; this mirrors
 * the same three hooks on Apothic's screen: the slot shows enabled, the hover tooltip flips to affordable, and a
 * "Level Requirement: X (was Y)" line is appended. Apothic emits several tooltips per frame, so the requirement
 * line targets the FIRST {@code setComponentTooltipForNextFrame} (the per-slot hover, {@code ordinal = 0}).
 *
 * <p>Targeted by string (no Apothic compile dep). The {@code extends} clause is compiler-only; it gives access
 * to {@code menu}/{@code minecraft}/{@code isHovering} via vanilla's base, which Apothic's screen also extends;
 * the base {@code menu} field holds the same menu instance Apothic stores, so {@code menu.costs} reads correctly.
 */
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentScreen", remap = false)
public abstract class ApothEnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {

    private ApothEnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @ModifyExpressionValue(method = "extractBackground",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
                    opcode = Opcodes.GETFIELD))
    private int chronicles_leveling$prodigyEnableSlot(int realLevel) {
        return EnchantingPerks.prodigyGateLevel(this.minecraft.player, realLevel);
    }

    @ModifyExpressionValue(method = "extractRenderState",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
                    opcode = Opcodes.GETFIELD))
    private int chronicles_leveling$prodigyTooltipAfford(int realLevel) {
        return EnchantingPerks.prodigyGateLevel(this.minecraft.player, realLevel);
    }

    /**
     * Hover tooltip: replace the slot's requirement line with Prodigy's reduced one (strips Apothic's own
     * un-reduced {@code container.enchant.level.requirement} line, shown when the player is below even the
     * reduced bar, and emits a single adaptive line). {@code ordinal = 0} = the per-slot hover tooltip.
     */
    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
                    ordinal = 0))
    private void chronicles_leveling$prodigyRequirementLine(
            GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci, @Local List<Component> texts) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            int base = this.menu.costs[i];
            if (base > 0 && this.isHovering(60, 14 + 19 * i, 108, 18, mouseX, mouseY)) {
                EnchantingPerks.decorateRequirementTooltip(player, base, texts);
                return;
            }
        }
    }
}
