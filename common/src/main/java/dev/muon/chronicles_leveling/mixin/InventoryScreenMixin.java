package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.client.screen.ChroniclesTabBar;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Chronicles tab strip to the vanilla inventory screen so the
 * Inventory / Stats / Attributes tabs are reachable from {@code E}.
 *
 * <p>{@code init()} runs on every resize and after vanilla
 * {@code clearWidgets()}, so we re-add the bar each time and don't need to
 * track whether one exists.
 *
 * <p>The {@code extends} clause is purely for the compiler — at runtime the
 * mixin merges into {@link InventoryScreen}'s actual hierarchy, which gives
 * us access to {@code leftPos}/{@code topPos} and the protected
 * {@code addRenderableWidget}.
 */
@Mixin(value = InventoryScreen.class, remap = false)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {

    private InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        throw new AssertionError("dummy ctor — never called at runtime");
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$addTabs(CallbackInfo ci) {
        this.addRenderableWidget(new ChroniclesTabBar(this.leftPos, this.topPos));
    }
}
