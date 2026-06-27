package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationData;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationStore;
import dev.muon.chronicles_leveling.skill.xp.BrewingXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player UI activity on a brewing-stand slot: routes through the BE
 * attachment so XP banked at brew time pays out on take and clears when
 * the player overwrites the slot.
 *
 * <p>The full brew XP is paid out on the first take (the value was computed
 * at brew time with the real count, so the taken stack's count is irrelevant;
 * on a shift-click it arrives empty anyway). Taking is not split across
 * partial takes: one brew, one payout.
 *
 * <p>{@code doBrew} mutates {@code items} via {@code items.set(...)}, not
 * through {@link Slot#set} (which only fires for menu-driven writes), so
 * vanilla brewing won't trip the "{@code onSet} clears the XP we just banked"
 * path. Only player UI writes flow through this mixin.
 *
 * <p>Server-side gate avoids touching client-side BE state; attachments
 * aren't synced, so client BEs have their own (always-default) state.
 */
@Mixin(Slot.class)
public class SlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$onTake(Player player, ItemStack carried, CallbackInfo ci) {
        Slot self = (Slot) (Object) this;
        if (!(self.container instanceof BrewingStandBlockEntity be)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        int slot = self.getContainerSlot();
        if (slot < 0 || slot > 2) return;

        BrewingStationStore store = Services.PLATFORM.getBrewingStationStore();
        BrewingStationData data = store.get(be);
        int pending = data.pendingXp(slot);
        if (pending <= 0) return;

        BrewingXpHandler.grantStored(serverPlayer, pending);
        store.set(be, data.withPendingXp(slot, 0));
    }

    @Inject(method = "set", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$onSet(ItemStack itemStack, CallbackInfo ci) {
        Slot self = (Slot) (Object) this;
        if (!(self.container instanceof BrewingStandBlockEntity be)) return;
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        int slot = self.getContainerSlot();
        if (slot < 0 || slot > 2) return;

        BrewingStationStore store = Services.PLATFORM.getBrewingStationStore();
        BrewingStationData data = store.get(be);
        if (data.pendingXp(slot) <= 0) return;
        store.set(be, data.withPendingXp(slot, 0));
    }
}
