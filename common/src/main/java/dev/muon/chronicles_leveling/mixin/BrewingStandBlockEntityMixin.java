package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationData;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets vanilla {@code doBrew} so the per-slot freshly-brewed flag on the
 * BE attachment ({@link BrewingStationData}) flips on for slots that actually
 * received a brew this tick. The persistent state lives on the centralized
 * attachment registered in {@code FabricAttachments}/{@code NeoforgeAttachments};
 * vanilla saves the BE and the attachment hitches a ride.
 *
 * <p>The pre-brew snapshot uses MixinExtras {@code @Share} so the array is
 * scoped to a single {@code doBrew} invocation — no static map, no orphaning
 * if any other mixin cancels {@code doBrew} between HEAD and RETURN.
 *
 * <p>{@code doBrew} is package-private on {@code BrewingStandBlockEntity}; the
 * AP can't resolve a remap entry, hence {@code remap = false}.
 */
@Mixin(BrewingStandBlockEntity.class)
public class BrewingStandBlockEntityMixin {

    @Inject(method = "doBrew", at = @At("HEAD"), remap = false)
    private static void chronicles_leveling$beforeBrew(
            Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci,
            @Share("snapshot") LocalRef<ItemStack[]> snapshot) {
        if (level.isClientSide()) return;
        snapshot.set(new ItemStack[]{
                items.get(0).copy(), items.get(1).copy(), items.get(2).copy()
        });
    }

    @Inject(method = "doBrew", at = @At("RETURN"), remap = false)
    private static void chronicles_leveling$afterBrew(
            Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci,
            @Share("snapshot") LocalRef<ItemStack[]> snapshot) {
        if (level.isClientSide()) return;
        ItemStack[] before = snapshot.get();
        if (before == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        BrewingStationStore store = Services.PLATFORM.getBrewingStationStore();
        BrewingStationData data = store.get(be);
        for (int i = 0; i < 3; i++) {
            ItemStack after = items.get(i);
            if (after.isEmpty()) continue;
            if (!ItemStack.matches(before[i], after)) {
                data = data.with(i, true);
            }
        }
        store.set(be, data);
    }
}
