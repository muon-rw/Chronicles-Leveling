package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.skill.xp.FishingXpHandler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FishingHook.class, remap = false)
public abstract class FishingHookMixin {

    @WrapOperation(
            method = "retrieve",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"),
            remap = false)
    private ObjectArrayList<ItemStack> chronicles_leveling$grantFishingXp(
            LootTable table, LootParams params, Operation<ObjectArrayList<ItemStack>> original) {
        ObjectArrayList<ItemStack> items = original.call(table, params);
        FishingHook self = (FishingHook) (Object) this;
        Player owner = self.getPlayerOwner();
        if (owner instanceof ServerPlayer player) {
            FishingXpHandler.onItemFished(player, items);
        }
        return items;
    }
}
