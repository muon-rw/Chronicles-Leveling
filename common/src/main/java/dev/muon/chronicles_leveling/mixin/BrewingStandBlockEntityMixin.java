package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.alchemy.BrewingPerks;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationData;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationStore;
import dev.muon.chronicles_leveling.skill.xp.BrewingXpHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brewing-stand alchemy hooks, all server-side:
 * <ul>
 *   <li>Catalysis timing perks: {@code serverTick}'s brew-time and fuel-refill constants scaled by the stand
 *       {@link BrewingPerks#owner owner}'s capabilities.</li>
 *   <li>The per-slot freshly-brewed flag on the BE attachment ({@link BrewingStationData}), set by bracketing
 *       {@code doBrew} so a slot that actually received a brew this tick flips on.</li>
 *   <li>Master Brewer: a per-slot roll grows a freshly-brewed output stack by one (potions are stackable).</li>
 *   <li>Count-preserving {@code mix}: a stacked bottle slot keeps its surplus through a re-brew instead of
 *       collapsing to a single potion.</li>
 *   <li>Bottle-slot insert cap: the Container insert path holds at most one item per bottle slot, so a bulk
 *       inserter cannot dump a full potion stack that the next brew tick would collapse and void.</li>
 * </ul>
 *
 * <p>The pre-brew snapshot uses MixinExtras {@code @Share} so the array is scoped to a single {@code doBrew}
 * invocation: no static map, no orphaning if another mixin cancels {@code doBrew} between HEAD and RETURN.
 */
@Mixin(BrewingStandBlockEntity.class)
public class BrewingStandBlockEntityMixin {

    @ModifyExpressionValue(method = "serverTick", at = @At(value = "CONSTANT", args = "intValue=400"), remap = false)
    private static int chronicles_leveling$catalysisBrewTime(int base, @Local(argsOnly = true, name = "entity") BrewingStandBlockEntity entity) {
        return BrewingPerks.brewTime(base, entity);
    }

    @ModifyExpressionValue(method = "serverTick", at = @At(value = "CONSTANT", args = "intValue=20"), remap = false)
    private static int chronicles_leveling$catalysisFuelUses(int base, @Local(argsOnly = true, name = "entity") BrewingStandBlockEntity entity) {
        return BrewingPerks.fuelUses(base, entity);
    }

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

        if (!(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)) return;

        double extraChance = BrewingPerks.extraBrewChance(stand);
        BrewingStationStore store = Services.PLATFORM.getBrewingStationStore();
        BrewingStationData data = store.get(stand);
        for (int i = 0; i < 3; i++) {
            ItemStack after = items.get(i);
            if (after.isEmpty()) continue;
            if (!ItemStack.matches(before[i], after)) {
                if (extraChance > 0.0
                        && after.getCount() < after.getMaxStackSize()
                        && level.getRandom().nextDouble() < extraChance) {
                    after.grow(1);   // Master Brewer: bonus potion stays in the slot (potions are stackable)
                }
                // Bank XP now (count includes the Master Brewer bonus); the taker's stack can't be read on shift-click.
                data = data.withPendingXp(i, BrewingXpHandler.computeXp(after));
            }
        }
        store.set(stand, data);
    }

    @WrapOperation(method = "doBrew", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;mix(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            remap = false), remap = false)
    private static ItemStack chronicles_leveling$preserveStackOnMix(
            PotionBrewing brewing, ItemStack ingredient, ItemStack source, Operation<ItemStack> original,
            @Local(argsOnly = true, name = "level") Level level, @Local(argsOnly = true, name = "pos") BlockPos pos) {
        ItemStack result = original.call(brewing, ingredient, source);
        if (result != source && !result.isEmpty()) {
            if (source.getCount() > 1) {
                result.setCount(Math.min(source.getCount(), result.getMaxStackSize()));   // mix() forgets the source count
            }
            if (level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand) {
                BrewingPerks.applyOutputPerks(stand, result, level);
            }
        }
        return result;
    }

    @ModifyReturnValue(method = "canPlaceItem", at = @At("RETURN"), remap = false)
    private boolean chronicles_leveling$capBottleSlotToOne(boolean original, int slot, ItemStack itemStack) {
        if (original && slot < 3 && itemStack.getCount() > 1) {
            return false;
        }
        return original;
    }
}
