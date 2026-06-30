package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.FishingSkill;
import dev.muon.chronicles_leveling.skill.fishing.FishingCatchHandler;
import dev.muon.chronicles_leveling.skill.fishing.FishingHooks;
import dev.muon.chronicles_leveling.skill.xp.FishingXpHandler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fishing-rod perks on the hook entity:
 * <ul>
 *   <li>Fishing XP + catch-loot perks, applied to the exact loot list {@code retrieve} iterates to spawn drops.
 *       Common (both loaders) because NeoForge's {@code ItemFishedEvent#getDrops} returns a detached copy that the
 *       spawn loop never re-reads, so mutating it there is a no-op; wrapping the loot roll mutates the real list.</li>
 *   <li>Patient Angler: faster bite by inflating {@code lureSpeed} below the re-roll floor.</li>
 *   <li>Frostbreaker: melt ice the bobber rests on so a holder can fish frozen water.</li>
 * </ul>
 */
@Mixin(value = FishingHook.class, remap = false)
public abstract class FishingHookMixin {

    @Shadow private int timeUntilLured;

    @Unique
    private static final int chronicles_leveling$DISCERNING_REROLLS = 8;

    @WrapOperation(
            method = "retrieve",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"),
            remap = false)
    private ObjectArrayList<ItemStack> chronicles_leveling$fishingCatch(
            LootTable table, LootParams params, Operation<ObjectArrayList<ItemStack>> original) {
        ObjectArrayList<ItemStack> items = original.call(table, params);
        FishingHook self = (FishingHook) (Object) this;
        Player owner = self.getPlayerOwner();
        if (owner instanceof ServerPlayer player) {
            if (SkillEffects.has(player, FishingSkill.NO_JUNK)) {
                // Discerning Fisher: reroll the whole catch (junk weight redistributes into fish/treasure) until junk-free.
                for (int tries = 0; tries < chronicles_leveling$DISCERNING_REROLLS && FishingXpHandler.containsJunk(items); tries++) {
                    items = original.call(table, params);
                }
            }
            FishingXpHandler.onItemFished(player, items);
            FishingCatchHandler.modifyCatch(player, items);
        }
        return items;
    }

    @ModifyExpressionValue(method = "catchingFish",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/projectile/FishingHook;lureSpeed:I",
                    opcode = Opcodes.GETFIELD),
            remap = false)
    private int chronicles_leveling$patientAngler(int lureSpeed) {
        double fraction = FishingHooks.biteSpeedFraction(((FishingHook) (Object) this).getPlayerOwner());
        if (fraction <= 0) {
            return lureSpeed;
        }
        // Cut the just-rolled bite wait by the perk fraction; cap so timeUntilLured still counts down (>= 1 tick).
        int cut = (int) Math.round(timeUntilLured * fraction);
        int maxCut = Math.max(0, timeUntilLured - lureSpeed - 1);
        return lureSpeed + Math.min(cut, maxCut);
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$frostbreaker(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        // onGround gates to the ice the bobber rests on, not every block it arcs over mid-flight.
        if (self.level().isClientSide() || !self.onGround()
                || !(self.getPlayerOwner() instanceof ServerPlayer player)
                || !SkillEffects.has(player, FishingSkill.FROSTBREAKER)) {
            return;
        }
        BlockPos pos = self.blockPosition();
        chronicles_leveling$melt(self.level(), pos);
        chronicles_leveling$melt(self.level(), pos.below());
    }

    /** Only melts plain/frosted ice; packed and blue ice are permanent build blocks and are left intact. */
    @Unique
    private static void chronicles_leveling$melt(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        }
    }
}
