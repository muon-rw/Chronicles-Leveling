package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.gather.CampfireOwnerData;
import dev.muon.chronicles_leveling.skill.gather.CampfireOwnerStore;
import dev.muon.chronicles_leveling.skill.gather.GardenersInfusionHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Gardener's Infusion: campfires cook autonomously with no take event, so credit the player who placed each item and
 * bake the boost onto the result when it pops. Ownership rides a per-slot BE attachment ({@link CampfireOwnerData} via
 * {@link CampfireOwnerStore}), so it persists through chunk unloads / restarts like the brewing-stand state.
 */
@Mixin(value = CampfireBlockEntity.class, remap = false)
public abstract class CampfireBlockEntityMixin {

    @Inject(method = "placeFood",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/CampfireBlockEntity;markUpdated()V"),
            remap = false)
    private void chronicles_leveling$trackFoodOwner(ServerLevel serverLevel, LivingEntity sourceEntity, ItemStack placeItem,
            CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) int slot) {
        CampfireBlockEntity self = (CampfireBlockEntity) (Object) this;
        UUID owner = sourceEntity instanceof ServerPlayer player ? player.getUUID() : null;
        CampfireOwnerStore store = Services.PLATFORM.getCampfireOwnerStore();
        CampfireOwnerData updated = store.get(self).with(slot, owner);
        if (updated != store.get(self)) {
            store.set(self, updated);
        }
    }

    @WrapOperation(method = "cookTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
            remap = false)
    private static void chronicles_leveling$infuseCookedFood(Level level, double x, double y, double z, ItemStack result,
            Operation<Void> original, @Local(argsOnly = true) CampfireBlockEntity entity, @Local(ordinal = 0) int slot) {
        CampfireOwnerStore store = Services.PLATFORM.getCampfireOwnerStore();
        UUID owner = store.get(entity).owner(slot);
        if (owner != null) {
            if (level instanceof ServerLevel serverLevel
                    && serverLevel.getPlayerByUUID(owner) instanceof ServerPlayer crafter) {
                GardenersInfusionHandler.infuse(crafter, result);
            }
            store.set(entity, store.get(entity).with(slot, null));
        }
        original.call(level, x, y, z, result);
    }
}
